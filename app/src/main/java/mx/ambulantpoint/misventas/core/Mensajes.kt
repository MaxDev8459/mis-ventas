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
    /** RF 2.4: el DFR pide la confirmación del vendedor pero no da texto (texto del equipo). */
    fun confirmarReactivarProducto(nombre: String) = "¿Desea reactivar el producto $nombre?"
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

    // Corregir venta (desarrollo independiente, no viene del DFR).
    fun confirmarCorregirVenta(totalFormateado: String, conDescuento: Boolean) =
        "Se cancelará la venta de $totalFormateado y sus productos se cargarán en una venta nueva para corregirla." +
            (if (conDescuento) " El descuento se vuelve a capturar al cobrar." else "") +
            " ¿Desea continuar?"
    const val VENTA_EN_CORRECCION = "Venta cancelada. Corrija los productos y vuelva a cobrar"
    fun productosNoRecargados(nombres: List<String>) =
        "No se cargaron por estar inactivos o sin existencia: ${nombres.joinToString(", ")}"

    // Merma desde la pantalla de venta.
    const val MERMA_REGISTRADA = "La merma se registró exitosamente"
    const val SIN_VENTAS_PERIODO = "No hay ventas registradas en este periodo"
    const val FORMA_PAGO_GUARDADA = "La forma de pago se guardó exitosamente"
    const val CONFIGURACION_GUARDADA = "La configuración se guardó exitosamente"
    const val DESCUENTO_NO_VALIDO = "El descuento no es válido"
    const val DESCUENTO_TOTAL_MINIMO = "El descuento debe dejar un total a cobrar de al menos \$1.00"

    /** Fiado y venta rápida (desarrollo independiente, Fase 3; textos del equipo, no del DFR). */
    object Fiado {
        const val NOMBRE_LONGITUD = "El nombre debe tener de 2 a 40 caracteres"
        const val CLIENTE_YA_REGISTRADO = "Ya hay un cliente con ese nombre"
        const val CLIENTE_GUARDADO = "El cliente se guardó exitosamente"
        const val ELIJA_CLIENTE = "Elija o escriba el cliente"
        const val ABONO_MAYOR_A_CERO = "El abono debe ser mayor a cero"
        const val SIN_SALDO = "El cliente no tiene saldo pendiente"
        fun abonoMayorAlSaldo(saldo: String) = "El abono es mayor a lo que debe ($saldo)"
        const val ABRA_CAJA_ABONO = "Abra caja para registrar el abono"
        const val ABONO_REGISTRADO = "El abono se registró exitosamente"
        const val ABONO_CANCELADO = "El abono se canceló"
        const val ABONO_NO_CANCELABLE = "Este abono ya no se puede cancelar"
        fun confirmarCancelarAbono(monto: String) = "¿Desea cancelar el abono de $monto? El saldo del cliente regresa"
        const val PAGO_INICIAL_TOTAL = "Si paga todo, no es fiado: cobre la venta normal"
        const val VENTA_FIADA = "Venta fiada registrada exitosamente"
        const val NO_FIAR_VARIOS = "Las ventas con monto libre no se pueden fiar"
        fun avisoSaldoAlto(cliente: String, debe: String, deberia: String) =
            "$cliente ya debe $debe. Con esta venta debería $deberia."
        fun devolucionAlCancelar(monto: String) = " Se devolverán $monto en efectivo al cliente."
        const val AVISO_GUARDADO = "El monto de aviso se guardó exitosamente"
        const val MONTO_LIBRE_MAYOR_A_CERO = "El monto debe ser mayor a cero"
    }

    /** Apartados (desarrollo independiente, Fase 4; textos del equipo, no del DFR). */
    object Apartado {
        const val LUGAR_LARGO = "El lugar debe tener hasta 60 caracteres"
        const val ENTREGA_PASADA = "La fecha y hora de entrega ya pasaron"
        const val APARTADO_GUARDADO = "El apartado se guardó exitosamente"
        const val APARTADO_ACTUALIZADO = "El apartado se actualizó exitosamente"
        const val APARTADO_CANCELADO = "El apartado se canceló. El producto vuelve a estar disponible"
        const val APARTADO_ENTREGADO = "Apartado entregado y venta registrada exitosamente"
        const val NO_PENDIENTE = "Este apartado ya no está pendiente"
        const val NO_APARTAR_VARIOS = "Los montos libres no se pueden apartar"
        const val NO_FIAR_APARTADO = "Los apartados se pagan completos al entregar"
        const val ABRA_CAJA_ENTREGAR = "Abra caja para entregar el apartado"
        fun confirmarCancelar(nombre: String) = "¿Desea cancelar el apartado de $nombre? El producto vuelve a estar disponible"
        fun ajusteDebajoDeApartado(apartado: String, producto: String) =
            "Hay $apartado de $producto en apartados. Cancele o cambie el apartado primero"
        fun seLiberan(nombres: List<String>) =
            " Se liberarán ${nombres.size} ${if (nombres.size == 1) "apartado no entregado" else "apartados no entregados"} (${nombres.joinToString(", ")})."
    }

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
    /** RF 6.6: si falla la generación del archivo. El DFR no da texto (texto del equipo, con el estilo del DFR). */
    const val REPORTE_NO_EXPORTADO = "El reporte no se exportó exitosamente, intente de nuevo"

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
     * Alertas de campo. No son texto del DFR (pide "alertas en pantalla" sin dar la redacción): las propuso
     * el equipo y el cliente las aprobó todas el 18-nov-2026 (Validacion_Textos_Cliente_Mis_Ventas.docx,
     * sin firma). CLABE_SOLO_NUMEROS (21-nov-2026) es del equipo, para la SC-01.
     */
    // Solicitud de cambio SC-01 (20-nov-2026): datos para transferencia. Textos del equipo, no del DFR.
    const val DATOS_TRANSFERENCIA_GUARDADOS = "Los datos para transferencia se guardaron exitosamente"
    const val DATOS_TRANSFERENCIA_BORRADOS = "Los datos para transferencia se borraron"

    object Campo {
        // SC-01 (textos del equipo).
        const val CLABE_INCOMPLETA = "La CLABE debe tener 18 dígitos"
        const val CLABE_SOLO_NUMEROS = "La CLABE solo debe tener números"
        const val CLABE_NO_VALIDA = "La CLABE no es válida. Revise los dígitos"
        const val OBLIGATORIO = "Este campo es obligatorio"
        const val CORREO_NO_VALIDO = "El correo electrónico no es válido"
        const val CORREO_YA_REGISTRADO = "El correo electrónico ya está registrado"
        const val TELEFONO_NO_VALIDO = "El teléfono debe tener 10 dígitos"
        const val CONTRASENA_NO_VALIDA = "La contraseña debe tener al menos 8 caracteres, con letras y números"
        const val CONTRASENAS_NO_COINCIDEN = "Las contraseñas no coinciden"
        const val ACEPTAR_AVISO = "Debe aceptar el aviso de privacidad"
        const val IMAGEN_OBLIGATORIA = "Agregue una imagen del producto"
        const val IMAGEN_NO_LEIDA = "No se pudo usar la imagen, elija otra"
        const val NOMBRE_YA_REGISTRADO = "Ese nombre ya está registrado"
        const val PRECIO_NO_VALIDO = "El precio debe ser mayor a cero, con hasta dos decimales"
        const val CANTIDAD_ENTERA = "La cantidad debe ser un número entero"
        const val CANTIDAD_KG = "La cantidad debe tener hasta tres decimales"
        const val CANTIDAD_MAYOR_CERO = "La cantidad debe ser mayor a cero"
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
