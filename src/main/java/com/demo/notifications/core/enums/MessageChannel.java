package com.demo.notifications.core.enums;

public final class MessageChannel {

   private MessageChannel() {
   }

   public static final String MSG_SUCCESS = "Notificación enviada correctamente \t";
   public static final String MSG_ERROR = "Error al enviar la notificación \t";
   public static final String MSG_REQUEST_NULL = "La solicitud de notificación no puede ser nula o vacía \t";
   public static final String MSG_CHANNEL_NOT_SUPPORTED = "El canal de notificación proporcionado no es compatible \t";
   public static final String MSG_CHANNEL_NOT_BLANK = "El canal de notificación proporcionado no puede estar vacío \t";
   public static final String MSG_SENDER_NULL = "El remitente proporcionado no puede ser nulo \t";
   public static final String MSG_BLANK_RECIPIENT = "El destinatario proporcionado no puede estar vacío \t";
   public static final String MSG_INVALID_EMAIL = "El correo electrónico proporcionado no es válido \t";
   public static final String MSG_INVALID_PHONE = "El número de teléfono proporcionado no es válido \t";
   public static final String MSG_BLANK_MESSAGE = "El mensaje proporcionado no puede estar vacío \t";
   public static final String MSG_BLANK_SUBJECT = "El asunto proporcionado no puede estar vacío \t";
   public static final String MSG_BLANK_TITLE = "El título proporcionado no puede estar vacío \t";
   public static final String MSG_PROVIDER_NOT_BLANK = "El proveedor proporcionado no puede estar vacío \t";
   public static final String MSG_PROVIDER_ALREADY_REGISTERED = "El proveedor ya está registrado para el canal \t";
   public static final String MSG_PROVIDER_SELECTION_REQUIRED = "Debe definir el proveedor activo cuando existen múltiples proveedores para el canal \t";
   public static final String MSG_PROVIDER_NOT_REGISTERED = "El proveedor activo no está registrado para el canal \t";
   public static final String MSG_PROVIDER_FALLBACK_REQUIRED = "Debe definir al menos un proveedor en la politica de fallback \t";
   public static final String MSG_PROVIDER_FALLBACK_DUPLICATED = "La politica de fallback contiene providers duplicados \t";
   public static final String MSG_PROVIDER_FALLBACK_INCOMPLETE = "La politica de fallback debe incluir exactamente los proveedores registrados para el canal \t";
   public static final String MSG_PROVIDER_POLICY_CONFLICT = "No se puede combinar proveedor activo y politica de fallback para el mismo canal \t";
   public static final String MSG_INVALID_PUSH_TOKEN = "El token de notificación push proporcionado no es válido \t";
   public static final String MSG_SENDER_REGISTRED = "Al menos uno de los remitentes debe estar registrado \t";
   public static final String MSG_EXECUTOR_REGISTRED = "El ejecutor no puede ser vacío, nulo o al menos debe estar registrado \t";
}
