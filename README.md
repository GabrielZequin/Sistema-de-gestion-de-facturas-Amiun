# Sistema de Gestión de Facturas – Amiun

Sistema web desarrollado para **automatizar la lectura, extracción y gestión de facturas de siniestros** de aseguradoras en la empresa Amiun (agencia de seguros).

La aplicación se conecta a una casilla de correo mediante **IMAP**, detecta nuevos correos con comprobantes, descarga los archivos PDF adjuntos, extrae los datos relevantes (número de factura, siniestro, orden, aseguradora y fecha) y los registra en una base de datos para su seguimiento y control.

Incluye una interfaz web que permite visualizar, filtrar y administrar facturas, asignar aseguradoras manualmente cuando sea necesario y controlar el ciclo completo de cada comprobante.

---

## 🚀 Tecnologías utilizadas

- Java 17  
- Spring Boot  
- Spring Data JPA  
- Spring Security  
- Thymeleaf  
- MySQL  
- PDFBox (extracción de texto desde PDFs)  
- Jakarta Mail (IMAP)  
- Bootstrap 5

---

## ⚙️ Funcionalidades principales

- Lectura automática de correos vía IMAP  
- Descarga y almacenamiento de PDFs de facturas  
- Extracción inteligente de datos desde comprobantes  
- Registro y gestión de facturas en base de datos  
- Filtros por estado, aseguradora y datos de búsqueda  
- Asignación manual de aseguradoras  
- Gestión de usuarios y roles (ADMIN / PDV)  
- Interfaz web responsive

## ▶️ Ejecución en desarrollo

1. Clonar el repositorio
2. Crear una base MySQL llamada `seguros_db`
3. Copiar `.env.example` a `.env` y completar si es necesario
4. Ejecutar:






