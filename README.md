# Taller-04-AREP

## Modularización con Virtualización e Introducción a Docker

Este proyecto implementa un **micro framework estilo Spring Boot** en Java puro que permite definir controladores REST usando anotaciones personalizadas (`@RestController`, `@GetMapping`, `@PostMapping`, etc.).  
Sobre esta base, se desarrolla una aplicación web de gestión de tareas con frontend en HTML/JS y backend en Java.

La aplicación permite:

- Agregar tareas con nombre y tipo (casa, universidad, trabajo, otro).  
- Listar las tareas en la interfaz web de forma dinámica.  
- Persistencia en memoria (las tareas se almacenan en una lista durante la ejecución del servidor).
- Desplegarse fácilmente en **contenedores Docker** y en **AWS EC2**.  

---

## 🚀 Video de Despliegue

[video](https://github.com/user-attachments/assets/4bb7d8d6-3e0a-46c4-91e8-7bafa848f831)


## ⚙️ Prerrequisitos

- **Java 17 o superior**  
- **Maven 3.8.1 o superior** (probado en 3.9.9)  
- **Docker** (para contenedores)
- Navegador web

---

## 🏗 Arquitectura

El proyecto sigue una arquitectura cliente–servidor:

- **Backend (Java)**  
  Servidor HTTP ligero basado en `MicroSpringBoot` que expone endpoints REST:
  - `GET /tasks` – Lista las tareas.
  - `POST /tasks` – Crea una nueva tarea.
  
- **Frontend (HTML, CSS, JavaScript)**  
  Interfaz web que permite:
  - Visualizar las tareas.
  - Agregar nuevas.

- **Contenedores Docker**  
  Imagen Docker para ejecutar la aplicación en cualquier entorno, incluyendo AWS.

![arquitectura](/imagenes/Arquitectura.png)
---

## ⚙️ Instalación

1. Clona este repositorio o descarga los archivos:  
   ```bash
   git clone https://github.com/juanescan/Taller-04-AREP.git
   cd Taller-04-AREP

2. Compila el proyecto con Maven:
    ```bash
   mvn clean install
3. Asegúrate de tener Java 11+ y Maven instalados.
## ▶️️ Ejecución

    java -cp target/classes eci.arep.juancancelado.microspringboot.MicroSpringBoot eci.arep.juancancelado.microspringboot.examples.TaskController 

![imagenes](/imagenes/Taller3.png)

El servidor se ejecutará en el puerto 8080:

👉 http://localhost:8080

## API REST

- GET /api/tasks → Listar tareas en JSON.
- POST /api/tasks → Agregar tarea enviando JSON:

{
  "name": "Estudiar AREP",
  "type": "Universidad"
}

## Estructura

```
src/
  main/
    java/
      eci/
        arep/
         juancancelado/ 
            httpserver/
                HttpServer.java
                Request.java
                Response.java
                Route.java
            microspringboot/
                annotations/
                    GetMapping.java
                    PostMapping.java
                    RequestBody.java
                    RequestParam.java
                    RestController.java
                examples/
                    GreetingController.java
                    TaskController.java
            model/
                Task.java
    webapp/
        fondo.png
        index.html
        script.js
        style.css
  test/
    java/
pom.xml
README.md

```

## Docker 🐳

### Generación de Imágenes Docker:

Para contruir la imagen del servidor con docker hacemos el siguiente comando:

```bash
   docker build --tag dockertask .
   ```

![docker](/imagenes/dockerimagen.png)

### Ejecución de los Contenedores:

Para crear los contenedores y correrlos localmente hacemos el siguiente comando:

```bash
   docker run -d -p 34000:8080 -e DOCKER_ENV=true --name dockercontainer dockertask
   ```

Creamos 3 contenedores

![contenedores](/imagenes/Contenedores.png)

### APP FUNCIONANDO

![funcionando](/imagenes/funcionando.png)

Despues de confirmar que la app esta corriendo en local host vamos a crear un repositorio dentro de dockerhub

![hub](/imagenes/hub.png)

y hacemos los siguientes comandos para subir la imagen al repositorio:

![login](/imagenes/login.png)

### Crear una instancia EC2:

nos conectamos via SSH a nuestra maquina:

![EC2](/imagenes/EC2.png)

luego instalamos Docker en la instancia:

```bash
   sudo yum update -y
   sudo yum install docker
   sudo service docker start
   ```

luego trasnferimos la imagen desde nuestro repositorio:

```bash
   docker run -d -p 42000:8080 -e DOCKER_ENV=true --name dockercontaineraws juanescan/taller4task
   ```

## ⚡ Main Features – HttpServer

Este `HttpServer` es un servidor HTTP ligero en **Java** para el *Gestor de Tareas*.  
Proporciona rutas REST, manejo de archivos estáticos, concurrencia y apagado elegante.

### 🛣️ Definición de Rutas REST
- Permite crear endpoints **GET** y **POST** con expresiones *lambda*.
- Ejemplo:
  ```java
  get("/hello", (req, res) -> "Hello " + req.getValues("name"));
  post("/api/tasks", (req, res) -> {
      Task t = new Task("Estudiar", "personal");
      addTask(t);
      return "{\"message\":\"Task added successfully\"}";
  });

### ⚙️ Concurrencia

Atiende múltiples solicitudes en paralelo usando un ExecutorService.

```java
private static final int THREADS = 10;
private static ExecutorService threadPool = Executors.newFixedThreadPool(THREADS);
```


## Test

![Test](/imagenes/testT3.png)

## APP

![App](/imagenes/APP1.png)

![App](/imagenes/APP2.png)

![App](/imagenes/APP3.png)

![App](/imagenes/APP4.png) 

![App](/imagenes/APP5.png) 

![App](/imagenes/APP6.png)

![App](/imagenes/APP7.png)

## Built With

- Java SE - Lenguaje de programación

- Maven - Herramienta de gestión de dependencias y construcción

## Authors 
- Juan Esteban Cancelado Sanchez - *AREP* *Taller 1* - [juanescan](https://github.com/juanescan)