package eci.arep.juancancelado.httpserver;

import eci.arep.juancancelado.model.Task;
import java.net.*;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiFunction;

public class HttpServer {

    private static final List<Task> tasks = Collections.synchronizedList(new ArrayList<>());

    private static final Map<String, BiFunction<Request, Response, String>> getRoutes = new ConcurrentHashMap<>();
    private static final Map<String, BiFunction<Request, Response, String>> postRoutes = new ConcurrentHashMap<>();
    private static String staticDirectory = "src/main/webapp";

    private static volatile boolean running = false;
    private static ServerSocket serverSocket = null;
    private static ExecutorService threadPool = null;

    public static void main(String[] args) {
        staticfiles("src/main/webapp");

        get("/hello", (req, res) -> "Hello " + req.getValues("name"));
        get("/pi", (req, res) -> String.valueOf(Math.PI));

        get("/api/tasks", (req, res) -> {
            synchronized (tasks) {
                if (tasks.isEmpty()) {
                    return "[]";
                }
                return toJson(tasks);
            }
        });

        post("/api/tasks", (req, res) -> {
            String body = req.getBody();
            Map<String, String> data = parseJson(body);

            if (data.containsKey("name") && data.containsKey("type")) {
                try {
                    Task t = new Task(data.get("name"), data.get("type"));
                    addTask(t);
                    return "{\"message\": \"Task added successfully\"}";
                } catch (NumberFormatException e) {
                    return "{\"error\": \"Invalid price format\"}";
                }
            }
            return "{\"error\": \"Missing fields\"}";
        });

        start(8080);
    }

    public static void get(String path, BiFunction<Request, Response, String> handler) {
        getRoutes.put(path, handler);
    }

    public static void post(String path, BiFunction<Request, Response, String> handler) {
        postRoutes.put(path, handler);
    }

    public static void staticfiles(String path) {
        staticDirectory = path;
        System.out.println("Archivos estáticos servidos desde: " + new File(staticDirectory).getAbsolutePath());
    }

    public static void start(int port) {
        // número de hilos: por defecto cores * 2, puedes ajustar
        int poolSize = Math.max(2, Runtime.getRuntime().availableProcessors() * 2);
        threadPool = Executors.newFixedThreadPool(poolSize);

        try {
            serverSocket = new ServerSocket(port);
            running = true;
            System.out.println("Servidor en ejecución en el puerto " + port + " con poolSize=" + poolSize);

            // Hook para apagado elegante
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutdown hook: iniciando apagado elegante...");
                stop();
            }));

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept(); // bloquea hasta conexión
                    // enviar la conexión al pool para manejo concurrente
                    threadPool.submit(() -> {
                        try {
                            handleRequest(clientSocket);
                        } catch (Exception e) {
                            e.printStackTrace();
                            try { clientSocket.close(); } catch (IOException ignored) {}
                        }
                    });
                } catch (SocketException se) {
                    // ocurre cuando serverSocket.close() es llamado desde stop()
                    if (running) {
                        se.printStackTrace();
                    } else {
                        // cierre intencional -> salir del loop
                        break;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            System.err.println("Error al iniciar el servidor: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } finally {
            // asegurar apagado si salimos del loop
            stop();
        }
    }

    public static void stop() {
        if (!running && (threadPool == null || threadPool.isShutdown())) {
            return; // ya detenido
        }
        running = false;
        System.out.println("Deteniendo servidor...");

        // Cerrar ServerSocket para desbloquear accept()
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Error cerrando ServerSocket: " + e.getMessage());
        }

        // Apagar el thread pool elegantemente
        if (threadPool != null) {
            threadPool.shutdown(); // no aceptar nuevas tareas
            try {
                if (!threadPool.awaitTermination(10, TimeUnit.SECONDS)) {
                    System.out.println("Forzando shutdown del pool...");
                    List<Runnable> dropped = threadPool.shutdownNow();
                    System.out.println("Tareas no ejecutadas: " + dropped.size());
                }
            } catch (InterruptedException e) {
                System.out.println("Interrupción esperando termination, forzando shutdownNow...");
                threadPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        System.out.println("Servidor detenido.");
    }

    private static void handleRequest(Socket clientSocket) {
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                OutputStream out = clientSocket.getOutputStream()
        ) {
            String requestLine = in.readLine();
            if (requestLine == null) {
                clientSocket.close();
                return;
            }

            System.out.println("Solicitud: " + requestLine);
            String[] requestParts = requestLine.split(" ");
            if (requestParts.length < 2) {
                clientSocket.close();
                return;
            }

            String method = requestParts[0];
            String fullPath = requestParts[1];
            String path = fullPath.split("\\?")[0];
            Map<String, String> queryParams = getQueryParams(fullPath);

            Request req = new Request(method, path, queryParams);
            Response res = new Response();

            if (method.equals("GET") && getRoutes.containsKey(path)) {
                String responseBody = getRoutes.get(path).apply(req, res);
                sendResponse(out, 200, "application/json", responseBody);
            } else if (method.equals("POST") && postRoutes.containsKey(path)) {
                String body = readRequestBody(in);
                req.setBody(body);
                String responseBody = postRoutes.get(path).apply(req, res);
                sendResponse(out, 201, "application/json", responseBody);
            } else {
                System.out.println("Buscando archivo estático en: " + staticDirectory + path);
                serveStaticFile(path, out);
            }

            clientSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
            try { clientSocket.close(); } catch (IOException ignored) {}
        }
    }

    private static String readRequestBody(BufferedReader in) throws IOException {
        StringBuilder body = new StringBuilder();
        int contentLength = 0;

        String line;
        while ((line = in.readLine()) != null && !line.isEmpty()) {
            if (line.startsWith("Content-Length:")) {
                try {
                    contentLength = Integer.parseInt(line.substring(15).trim());
                } catch (NumberFormatException ignored) {}
            }
        }

        if (contentLength > 0) {
            char[] buffer = new char[contentLength];
            int read = in.read(buffer, 0, contentLength);
            if (read > 0) body.append(buffer, 0, read);
        }

        String jsonBody = body.toString();
        System.out.println("JSON Recibido: " + jsonBody);
        return jsonBody;
    }

    private static Map<String, String> getQueryParams(String fullPath) {
        Map<String, String> queryParams = new HashMap<>();
        if (fullPath.contains("?")) {
            String queryString = fullPath.split("\\?")[1];
            String[] params = queryString.split("&");

            for (String param : params) {
                String[] keyValue = param.split("=", 2);
                if (keyValue.length == 2) {
                    queryParams.put(keyValue[0], keyValue[1]);
                }
            }
        }
        return queryParams;
    }

    private static void serveStaticFile(String path, OutputStream out) throws IOException {
        if (path.equals("/")) {
            path = "/index.html";
        }
        File file = new File(staticDirectory + path);
        if (file.exists() && !file.isDirectory()) {
            String contentType = getContentType(path);
            byte[] fileBytes = Files.readAllBytes(file.toPath());
            out.write(("HTTP/1.1 200 OK\r\nContent-Type: " + contentType + "\r\n\r\n").getBytes());
            out.write(fileBytes);
        } else {
            out.write("HTTP/1.1 404 Not Found\r\n\r\n".getBytes());
        }
    }

    private static void sendResponse(OutputStream out, int statusCode, String contentType, String body) throws IOException {
        String statusLine = "HTTP/1.1 " + statusCode + " " + getStatusMessage(statusCode) + "\r\n";
        String headers = "Content-Type: " + contentType + "\r\n"
                + "Content-Length: " + body.getBytes().length + "\r\n"
                + "Connection: close\r\n\r\n";

        out.write((statusLine + headers + body).getBytes());
        out.flush();
    }

    private static String getStatusMessage(int statusCode) {
        return switch (statusCode) {
            case 200 -> "OK";
            case 201 -> "Created";
            case 400 -> "Bad Request";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 500 -> "Internal Server Error";
            default -> "Unknown Status";
        };
    }

    private static String getContentType(String path) {
        if (path.endsWith(".html")) return "text/html";
        if (path.endsWith(".css")) return "text/css";
        if (path.endsWith(".js")) return "application/javascript";
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".jpeg") || path.endsWith(".jpg")) return "image/jpeg";
        if (path.endsWith(".gif")) return "image/gif";
        if (path.endsWith(".svg")) return "image/svg+xml";
        if (path.endsWith(".ico")) return "image/x-icon";
        return "application/octet-stream";
    }

    public static Map<String, String> parseJson(String json) {
        Map<String, String> map = new HashMap<>();

        if (json == null || json.isEmpty()) {
            return map;
        }

        json = json.trim();

        if (json.startsWith("{") && json.endsWith("}")) {
            json = json.substring(1, json.length() - 1);
        } else {
            return map;
        }

        String[] pairs = json.split(",");

        for (String pair : pairs) {
            String[] keyValue = pair.split(":", 2);
            if (keyValue.length == 2) {
                String key = keyValue[0].trim().replace("\"", "");
                String value = keyValue[1].trim().replace("\"", "");
                map.put(key, value);
            }
        }
        return map;
    }

    public static String toJson(List<Task> tasks) {
        StringBuilder json = new StringBuilder("[");
        synchronized (tasks) {
            for (int i = 0; i < tasks.size(); i++) {
                Task task = tasks.get(i);
                json.append("{")
                        .append("\"name\":\"").append(task.getName()).append("\",")
                        .append("\"type\":\"").append(task.getType()).append("\"")
                        .append("}");
                if (i < tasks.size() - 1) {
                    json.append(",");
                }
            }
        }
        json.append("]");
        return json.toString();
    }

    public static List<Task> getTasks() {
        return tasks;
    }

    public static void addTask(Task task) {
        tasks.add(task);
    }

    public static void clearTasks() {
        tasks.clear();
    }

    public static Map<String, BiFunction<Request, Response, String>> getRoutes() {
        return getRoutes;
    }

    public static Map<String, BiFunction<Request, Response, String>> postRoutes() {
        return postRoutes;
    }

}
