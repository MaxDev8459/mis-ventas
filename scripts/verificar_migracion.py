#!/usr/bin/env python3
"""
Verifica las migraciones de Room con sqlite3 (sin teléfono).

Aplica EXACTAMENTE las sentencias `db.execSQL(...)` de los bloques `MIGRACION_a_b` de MisVentasDb.kt, en cadena,
y compara el resultado con el esquema exportado por Room (`schemas/.../N.json`): columnas (tipo, NOT NULL, valor
por defecto, llave primaria), llaves foráneas e índices. Además revisa que ninguna tabla pierda filas, que los
valores de las columnas que ya existían no cambien y que no aparezcan llaves foráneas rotas nuevas.

Uso:
  scripts/verificar_migracion.py                     # base v6 sintética con datos → última versión
  scripts/verificar_migracion.py copia.db            # copia real (por ejemplo, de Mis Ventas Pruebas) → última versión

Para una copia de Mis Ventas Pruebas (app debug):
  adb exec-out run-as mx.ambulantpoint.misventas.pruebas cat databases/mis_ventas.db > copia.db   (y -wal, -shm)
Nunca se modifica el archivo original: se trabaja sobre una copia temporal.
"""
import json, os, re, shutil, sqlite3, sys, tempfile

RAIZ = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'app')
ESQ = os.path.join(RAIZ, 'schemas', 'mx.ambulantpoint.misventas.data.local.MisVentasDb')
KT = os.path.join(RAIZ, 'src', 'main', 'java', 'mx', 'ambulantpoint', 'misventas', 'data', 'local', 'MisVentasDb.kt')
CADENA = r'((?:"(?:[^"\\]|\\.)*"\s*\+?\s*)+)'


def sentencias(origen, destino):
    src = open(KT, encoding='utf-8').read()
    m = re.search(rf'val MIGRACION_{origen}_{destino} = object : Migration\({origen}, {destino}\) \{{', src)
    if not m:
        sys.exit(f'No encontré MIGRACION_{origen}_{destino}')
    # El bloque termina donde empieza la siguiente declaración de migración o el fin del companion.
    resto = src[m.end():]
    fin = re.search(r'\n        (/\*\*|val MIGRACION_)', resto)
    bloque = resto[:fin.start() if fin else len(resto)]
    variables = {v.group(1): ''.join(re.findall(r'"((?:[^"\\]|\\.)*)"', v.group(2)))
                 for v in re.finditer(r'val (\w+) = ' + CADENA, bloque)}
    salida = []
    for e in re.finditer(r'db\.execSQL\(\s*' + CADENA + r',?\s*\)', bloque):
        s = ''.join(re.findall(r'"((?:[^"\\]|\\.)*)"', e.group(1)))
        salida.append(re.sub(r'\$(\w+)', lambda x: variables[x.group(1)], s))
    return salida


def esquema(n):
    return json.load(open(os.path.join(ESQ, f'{n}.json')))['database']


def crear(con, n):
    d = esquema(n)
    for e in d['entities']:
        con.execute(e['createSql'].replace('${TABLE_NAME}', e['tableName']))
        for i in e.get('indices', []):
            con.execute(i['createSql'].replace('${TABLE_NAME}', e['tableName']))
    con.execute(f'PRAGMA user_version = {n}')


def datos_v6(con):
    x = con.execute
    x("INSERT INTO configuracion (id, descuentosHabilitados, actualizadoEn, pendienteSync) VALUES ('configuracion',0,1,0)")
    x("INSERT INTO categoria VALUES ('cat','Pan','pan',1,1,0)")
    x("INSERT INTO producto (id,nombre,nombreNormalizado,categoriaId,tipoVenta,precioCentavos,existenciaMilesimas,imagenRuta,activo,favorito,creadoEn,actualizadoEn,pendienteSync) "
      "VALUES ('p1','Carlota','carlota','cat','UNIDAD',4000,10000,'',1,1,1,1,0)")
    x("INSERT INTO forma_pago VALUES ('ef','Efectivo','efectivo',1,1,1,1,0)")
    x("INSERT INTO forma_pago VALUES ('tr','Transferencia','transferencia',1,0,1,1,0)")
    x("INSERT INTO caja (id,estado,fondoCentavos,abiertaEn,cerradaEn,corteCalculadoEn,corteVigente,totalVendidoCentavos,numeroVentas,"
      "descuentosCentavos,efectivoEsperadoCentavos,efectivoContadoCentavos,diferenciaCentavos,creadoEn,actualizadoEn,pendienteSync) "
      "VALUES ('k1','CERRADA',10000,1,9,8,1,12000,2,0,18000,18000,0,1,9,0)")
    x("INSERT INTO caja (id,estado,fondoCentavos,abiertaEn,corteVigente,creadoEn,actualizadoEn,pendienteSync) VALUES ('k2','ABIERTA',5000,10,0,10,10,1)")
    for i, (k, f, est, desc) in enumerate([('k1', 'ef', 'REGISTRADA', None), ('k1', 'tr', 'CANCELADA', 'PORCENTAJE'), ('k2', 'ef', 'REGISTRADA', None)]):
        x(f"INSERT INTO venta VALUES ('v{i}','{k}',{i+2},8000,{'NULL' if desc is None else repr(desc)},{'NULL' if desc is None else 1000},"
          f"{0 if desc is None else 800},{8000 if desc is None else 7200},'{f}',{'10000' if f=='ef' else 'NULL'},{'2000' if f=='ef' else 'NULL'},"
          f"'{est}',{'5' if est=='CANCELADA' else 'NULL'},{i+2},{i+2},{i%2})")
        x(f"INSERT INTO venta_detalle VALUES ('d{i}','v{i}','p1','Carlota','UNIDAD',2000,4000,8000,{i+2},0)")
        x(f"INSERT INTO movimiento_inventario VALUES ('m{i}','p1',{i+2},'VENTA',-2000,8000,NULL,NULL,'v{i}',{i+2},0)")
    x("INSERT INTO corte_forma_pago VALUES ('k1','ef','Efectivo',1,8000,9,0)")
    x("INSERT INTO carga_caja VALUES ('k2','p1',5000,10,10,1)")


def tablas(con):
    return [r[0] for r in con.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' "
                                       "AND name NOT IN ('room_master_table','android_metadata')")]


def columnas(con, t):
    return [r[1] for r in con.execute(f'PRAGMA table_info(`{t}`)')]


def esquema_real(con, t):
    cols = {r[1]: (r[2].upper(), bool(r[3]), r[4], r[5]) for r in con.execute(f'PRAGMA table_info(`{t}`)')}
    fks = sorted((r[2], r[3], r[4]) for r in con.execute(f'PRAGMA foreign_key_list(`{t}`)'))
    idx = {r[1]: (bool(r[2]), [c[2] for c in con.execute(f'PRAGMA index_info(`{r[1]}`)')])
           for r in con.execute(f'PRAGMA index_list(`{t}`)') if not r[1].startswith('sqlite_autoindex')}
    return cols, fks, idx


def esquema_json(e):
    pk = e['primaryKey']['columnNames']
    cols = {f['columnName']: (f['affinity'], f.get('notNull', False), f.get('defaultValue'),
                              pk.index(f['columnName']) + 1 if f['columnName'] in pk else 0) for f in e['fields']}
    fks = sorted((fk['table'], c, r) for fk in e.get('foreignKeys', []) for c, r in zip(fk['columns'], fk['referencedColumns']))
    idx = {i['name']: (i['unique'], i['columnNames']) for i in e.get('indices', [])}
    return cols, fks, idx


def main():
    ultima = max(int(f[:-5]) for f in os.listdir(ESQ) if f.endswith('.json'))
    tmp = tempfile.mkdtemp()
    ruta = os.path.join(tmp, 'v.db')
    if len(sys.argv) > 1:
        for suf in ('', '-wal', '-shm'):
            if os.path.exists(sys.argv[1] + suf):
                shutil.copy(sys.argv[1] + suf, ruta + suf)
        con = sqlite3.connect(ruta)
        origen = con.execute('PRAGMA user_version').fetchone()[0]
        print(f'Copia real en v{origen}')
    else:
        con = sqlite3.connect(ruta)
        origen = 6
        crear(con, 6)
        datos_v6(con)
        print('Base sintética v6 con datos')
    con.commit()
    if origen >= ultima:
        sys.exit(f'La base ya está en v{origen}; no hay migración que probar')

    antes = {t: (columnas(con, t), con.execute(f'SELECT * FROM `{t}`').fetchall()) for t in tablas(con)}
    rotas_antes = len(con.execute('PRAGMA foreign_key_check').fetchall())

    for v in range(origen, ultima):
        sql = sentencias(v, v + 1)
        con.execute('BEGIN')
        for s in sql:
            con.execute(s)
        con.execute(f'PRAGMA user_version = {v + 1}')
        con.execute('COMMIT')
        print(f'MIGRACION_{v}_{v + 1}: {len(sql)} sentencias aplicadas')

    errores = []
    destino = esquema(ultima)
    for e in destino['entities']:
        t = e['tableName']
        for nombre, a, b in zip(('columnas', 'llaves foráneas', 'índices'), esquema_real(con, t), esquema_json(e)):
            if a != b:
                errores.append(f'{t}: {nombre} distintas\n  real={a}\n  {ultima}.json={b}')
    for t, (cols, filas) in antes.items():
        ahora = con.execute(f"SELECT {','.join('`' + c + '`' for c in cols)} FROM `{t}`").fetchall()
        if sorted(map(repr, ahora)) != sorted(map(repr, filas)):
            errores.append(f'{t}: cambiaron las filas o sus valores ({len(filas)} antes, {len(ahora)} después)')
    rotas = len(con.execute('PRAGMA foreign_key_check').fetchall())
    if rotas > rotas_antes:
        errores.append(f'llaves foráneas rotas: {rotas_antes} → {rotas}')

    print('Filas por tabla (sin cambios):', {t: len(f) for t, (_, f) in antes.items() if f})
    print('Tablas nuevas:', sorted(set(e['tableName'] for e in destino['entities']) - set(antes)))
    print('Llaves foráneas rotas antes/después:', rotas_antes, rotas)
    print(f'RESULTADO: OK, coincide con {ultima}.json y sin pérdida ni cambio de datos' if not errores
          else 'ERRORES\n' + '\n'.join(errores))
    shutil.rmtree(tmp)
    sys.exit(1 if errores else 0)


main()
