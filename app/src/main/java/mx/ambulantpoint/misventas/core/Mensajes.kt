package mx.ambulantpoint.misventas.core

/**
 * Mensajes al usuario. La sección DFR está copiada literal de DFR_Mis_Ventas_v1.2 (no editar sin
 * Solicitud de Cambio). Los textos entre corchetes del DFR se arman con las funciones.
 */
object Mensajes {

    // ---------- Módulo 1. Usuarios ----------
    const val CUENTA_REGISTRADA = "La cuenta se registró exitosamente"
    const val CUENTA_NO_REGISTRADA = "La cuenta no se registró exitosamente, intente de nuevo"
    const val REGISTRO_SIN_INTERNET = "Se requiere conexión a internet para registrarse"
    const val CREDENCIALES_INCORRECTAS = "Correo electrónico o contraseña incorrectos"
    const val LOGIN_SIN_INTERNET = "Se requiere conexión a internet para iniciar sesión"
    const val CONFIRMAR_CERRAR_SESION = "¿Desea cerrar sesión?"
    const val PENDIENTES_AL_CERRAR_SESION =
        "Tiene registros pendientes de sincronizar. Conéctese a internet para sincronizarlos antes de cerrar sesión"
    const val RECUPERAR_CONTRASENA_ENVIADO =
        "Si el correo está registrado, recibirá un enlace para restablecer su contraseña"
    const val RECUPERAR_SIN_INTERNET = "Se requiere conexión a internet para recuperar la contraseña"
    const val PERFIL_ACTUALIZADO = "Los datos se actualizaron exitosamente"

    // ---------- Módulo 2. Catálogo ----------
    const val PRODUCTO_REGISTRADO = "El producto se registró exitosamente"
    const val PRODUCTO_NO_REGISTRADO = "El producto no se registró exitosamente, intente de nuevo"
    const val PRODUCTO_ACTUALIZADO = "El producto se actualizó exitosamente"
    fun confirmarDesactivarProducto(nombre: String) =
        "¿Desea desactivar el producto $nombre? Ya no estará disponible para la venta"
    const val PRODUCTO_DESACTIVADO = "El producto se desactivó exitosamente"
    const val PRODUCTO_REACTIVADO = "El producto se reactivó exitosamente"
    const val SIN_PRODUCTOS = "No se encontraron productos"
    const val CATEGORIA_GUARDADA = "La categoría se guardó exitosamente"
    const val SIN_CATEGORIAS = "No hay categorías registradas"
    fun confirmarEliminarCategoria(nombre: String) = "¿Desea eliminar la categoría $nombre?"
    const val CATEGORIA_ELIMINADA = "La categoría se eliminó exitosamente"
    const val CATEGORIA_CON_PRODUCTOS = "No es posible eliminar la categoría porque tiene productos asignados"

    // ---------- Módulo 3. Ventas ----------
    /** "Solo hay [existencia] [pzas/kg] disponibles de [producto]". */
    fun soloHayDisponibles(existencia: String, producto: String) = "Solo hay $existencia disponibles de $producto"
    const val AGREGUE_UN_PRODUCTO = "Agregue al menos un producto a la venta"
    const val MONTO_RECIBIDO_MENOR = "El monto recibido es menor al total de la venta"
    const val VENTA_REGISTRADA = "Venta registrada exitosamente"
    /** "¿Desea cancelar la venta de $[total]? Las existencias se regresarán al inventario". */
    fun confirmarCancelarVenta(totalFormateado: String) =
        "¿Desea cancelar la venta de $totalFormateado? Las existencias se regresarán al inventario"
    const val VENTA_CANCELADA = "La venta se canceló exitosamente"
    const val SIN_VENTAS_PERIODO = "No hay ventas registradas en este periodo"
    const val FORMA_PAGO_GUARDADA = "La forma de pago se guardó exitosamente"
    const val CONFIGURACION_GUARDADA = "La configuración se guardó exitosamente"
    const val DESCUENTO_NO_VALIDO = "El descuento no es válido"
    const val DESCUENTO_TOTAL_MINIMO = "El descuento debe dejar un total a cobrar de al menos \$1.00"

    // ---------- Módulo 4. Inventario ----------
    const val ENTRADA_REGISTRADA = "La entrada se registró exitosamente"
    const val AJUSTE_REGISTRADO = "El ajuste se registró exitosamente"
    const val DISMINUCION_MAYOR = "La cantidad a disminuir es mayor a la existencia actual"
    const val SIN_MOVIMIENTOS_PERIODO = "No hay movimientos registrados en este periodo"
    /** "Quedan [existencia] [pzas/kg] de [producto]. Considere surtirlo". */
    fun existenciaBaja(existencia: String, producto: String) = "Quedan $existencia de $producto. Considere surtirlo"

    // ---------- Módulo 5. Caja ----------
    const val CAJA_ABIERTA = "La caja se abrió exitosamente"
    const val ABRA_CAJA = "Abra caja para comenzar a vender"
    const val CONFIRMAR_CERRAR_CAJA = "¿Desea cerrar la caja? Las ventas de esta caja ya no podrán cancelarse"
    const val CAJA_CERRADA = "La caja se cerró exitosamente"
    const val NOTIFICACION_CAJA_ABIERTA = "¿Aún estás vendiendo? No has hecho tu cierre de caja"
    const val SIN_CORTES_PERIODO = "No hay cortes de caja en este periodo"
    const val SOBRANTE = "Sobrante"
    const val FALTANTE = "Faltante"
    const val SIN_DIFERENCIA = "Sin diferencia"

    // ---------- Módulo 6. Reportes ----------
    const val REPORTE_EXPORTADO = "El reporte se exportó exitosamente"
    const val SIN_INFORMACION_EXPORTAR = "No hay información para exportar"

    // ---------- Módulo 7. Sincronización ----------
    const val SINCRONIZADO = "Sincronizado"
    const val SINCRONIZANDO = "Sincronizando…"
    fun registrosPendientes(n: Int) = "$n registros pendientes"
    const val SIN_CONEXION = "Sin conexión"
    const val AVISO_DATOS_MOVILES =
        "Está conectado con datos móviles. La sincronización puede consumir datos de su plan. ¿Desea sincronizar?"
    const val BOTON_SINCRONIZAR = "Sincronizar"
    const val BOTON_CANCELAR = "Cancelar"
    const val BOTON_SINCRONIZAR_AHORA = "Sincronizar ahora"
    const val SINCRONIZACION_EXITOSA = "La información se sincronizó exitosamente"
    const val SYNC_SIN_INTERNET = "Sin conexión a internet. La información se sincronizará cuando se restablezca la conexión"
    const val YA_SINCRONIZADO = "Su información ya está sincronizada"
    const val RECUPERANDO = "Recuperando su información…"
    const val RECUPERACION_FALLIDA = "No se pudo completar la recuperación. Conéctese a internet e intente nuevamente"
    const val CUENTA_SE_CERRARA_EN_OTRO =
        "Su cuenta se cerrará en el otro dispositivo. La información pendiente en ese dispositivo se enviará cuando tenga conexión"
    const val SESION_EN_OTRO_DISPOSITIVO = "Su sesión se inició en otro dispositivo"
    const val NOTIFICACION_SIN_RESPALDO = "Llevas 3 días sin respaldar tu información. Conéctate a internet para sincronizar"

    /**
     * Alertas de campo. PROPUESTA DEL EQUIPO, NO SON TEXTO DEL DFR: el DFR pide "alertas en pantalla"
     * (campos sin llenar, correo no válido, etc.) sin dar la redacción. Pendiente de validar con el cliente.
     */
    object Campo {
        const val OBLIGATORIO = "Este campo es obligatorio"
        const val CORREO_NO_VALIDO = "El correo electrónico no es válido"
        const val CORREO_YA_REGISTRADO = "El correo electrónico ya está registrado"
        const val TELEFONO_NO_VALIDO = "El teléfono debe tener 10 dígitos"
        const val CONTRASENA_NO_VALIDA = "La contraseña debe tener al menos 8 caracteres, con letras y números"
        const val CONTRASENAS_NO_COINCIDEN = "Las contraseñas no coinciden"
        const val ACEPTAR_AVISO = "Debe aceptar el aviso de privacidad"
        const val IMAGEN_OBLIGATORIA = "Agregue una imagen del producto"
        const val NOMBRE_YA_REGISTRADO = "Ese nombre ya está registrado"
        const val PRECIO_NO_VALIDO = "El precio debe ser mayor a cero, con hasta dos decimales"
        const val CANTIDAD_ENTERA = "La cantidad debe ser un número entero"
        const val CANTIDAD_KG = "La cantidad debe tener hasta tres decimales"
        const val CANTIDAD_MAYOR_CERO = "La cantidad debe ser mayor a cero"
        const val EXISTENCIA_NO_VALIDA = "La existencia no es válida para el tipo de venta"
        const val MONTO_NO_VALIDO = "El monto no es válido"
        const val ESPECIFIQUE_MOTIVO = "Especifique el motivo"
        const val TIPO_VENTA_CON_EXISTENCIA = "Solo puede cambiar el tipo de venta si la existencia es cero"
        const val EFECTIVO_NO_MODIFICABLE = "La forma de pago Efectivo no puede modificarse ni desactivarse"
        const val CORTE_PENDIENTE = "Realice o actualice el corte antes de cerrar la caja"
        const val CAJA_YA_ABIERTA = "Ya hay una caja abierta"
        const val PRODUCTO_NO_DISPONIBLE = "El producto ya no está disponible para la venta"
        const val FORMA_PAGO_NO_DISPONIBLE = "La forma de pago no está disponible"
        const val VENTA_NO_CANCELABLE = "Esta venta ya no puede cancelarse"
        const val NO_ENCONTRADO = "No se encontró el registro"
    }
}
