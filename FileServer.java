/**
 * A simple file server that serves files from a specified folder. The server listens for HTTP requests and serves
 * files from the specified folder based on the requested path. The server can be started from the command line with
 * the following usage:
 *   java FileServer [-pPORT] [ROOT_FOLDER]
 * 
 * The server listens on port PORT (default 8080) and serves files from the ROOT_FOLDER (default ./public). The server
 * can serve files with the following extensions: html, css, js, png, jpg, jpeg, gif, ico, and svg. All other files are
 * served as text/plain.
 * 
 * A server can be created and started using the buildFileServer method, which creates a new HTTP server that serves
 * files from the specified root folder. The server can be started by calling the start() method on the returned server.
 * Additional handlers can be added to the server to serve requests for specific paths using the createContext method on
 * the server returned by buildFileServer.
 */

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.stream.Stream;
import java.util.Map;
import java.util.HashMap;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class FileServer implements HttpHandler {

    private final Path rootFolder;
    private final Map<String, Path> fileMap;
    private long cacheTime;

    private static final long CACHE_LIMIT = 5000; // 5 seconds
    
    /**
     * Create a new FileServer that serves files from the specified folder
     * @param rootFolder The folder to serve files from
     */
    private FileServer(String rootFolder) {
        this.rootFolder = new File(rootFolder).toPath();

        fileMap = new HashMap<String, Path>();
        updateFiles();
    }

    /**
     * Handle an incoming HTTP request
     * @param exchange The HTTP exchange containing the request and response
     */
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        final String requestMethod = exchange.getRequestMethod();
        String requestPath = exchange.getRequestURI().getPath();

        // Log the request
        System.out.println(new Date() + " " + requestMethod + " request for: " + requestPath);

        // Attempt to serve a file from the public folder for any GET request
        if ("GET".equals(requestMethod)) {

            // If the request is for a folder, serve the index.html file it contains
            if (requestPath.endsWith("/")) {
                requestPath += "index.html";
            }


            // Serve the requested file
            handleFileRequest(exchange, requestPath);
        }
        else {
            // If the request is not a GET request, return a 405 Method Not Allowed error
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_BAD_METHOD, 0);
            exchange.getResponseBody().close();
        }
    }

    /**
     * Attempt to serve the requested file from the public folder
     * @param exchange The HTTP exchange containing the request and response
     * @param requestedPath The path of the requested file
     */
    private void handleFileRequest(HttpExchange exchange, String requestedPath) {
        int errorCode = HttpURLConnection.HTTP_NOT_FOUND; // If there is an error, assume it's a 404 error
        
        // Replace path separators in the request path with the system's file separator
        requestedPath = requestedPath.replace("/", File.separator);

        // Check if the requested file exists in the root folder
        Path file = findFile(requestedPath);

        // If the file exists, attempt to send it to the client
        if (file != null) {
            System.out.println(new Date() + " Sending file: " + file.toString());
            byte[] contents = new byte[0];

            // Attempt to read the file contents
            try {
                contents = Files.readAllBytes(file);
            } 
            catch (IOException e) {
                // If there was an error reading the file, set the status code and log the error
                System.err.println(new Date() + " Error reading file: " + e);
                errorCode = HttpURLConnection.HTTP_INTERNAL_ERROR;
                updateFiles(); // Refresh the file list in case the file was deleted
            }

            if (contents.length > 0) {    
                try {
                    // Set the response headers and status code
                    exchange.getResponseHeaders().add("Content-Type", getContentType(file));
                    exchange.getResponseHeaders().add("Cache-Control", "max-age=300");
                    exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, contents.length);
        
                    // send the file contents
                    OutputStream response = exchange.getResponseBody();
                    response.write(contents);
                    response.close();

                    // The file was sent successfully, so return and let the errors fall through
                    return; 
                }
                catch (IOException e) {
                    // If there was an error sending the file, log the error
                    System.err.println(new Date() + " Error sending file: " + e);
                    errorCode = HttpURLConnection.HTTP_INTERNAL_ERROR;
                }
            }
        }

        // There was an error.
        // If the file doesn't exist, send a 404 error.
        // If there was an error reading the file, send a 500 error.
        try {
            System.out.println(new Date() + " File not found: " + requestedPath);
            exchange.sendResponseHeaders(errorCode, 0);
            exchange.getResponseBody().close();
        } 
        catch (IOException e) {
            // If there was an error sending the error message, log the error
            System.err.println(new Date() + " Couldn't send error message." + e);
        }
    }
    
    /**
     * Find the file using the cached list of available files. If the file is not found, the file list 
     * is refreshed and the file is searched for again, allowing for new files to be added to the server.
     * 
     * @param requestedPath The path of the requested file
     * @return
     */
    private Path findFile(String requestedPath) {
        Path file = fileMap.get(requestedPath);
        
        // If the file is not found in the cache, try to find it again, but limit to once every 5 seconds
        if (file == null && (System.currentTimeMillis() - cacheTime) > CACHE_LIMIT) {
            System.out.println(new Date() + " Refreshing file list");
            updateFiles();
            file = fileMap.get(requestedPath);
        }

        return file;
    }

    /**
     * Map the files in the root folder to request paths they will be accessed from.
     */
    private void updateFiles() {
        // Get an up-to-date list of the files in the root folder
        final Path[] files = fileList(rootFolder);
        fileMap.clear();

        // Loop through the files found and add them to the file map for quick access
        for (Path file : files) {
            fileMap.put(File.separator + rootFolder.relativize(file).toString(), file);
        }

        // Update the cache time to the current time
        cacheTime = System.currentTimeMillis();
    }

    /**
     * Recursively get a list of files in a folder
     * @param folder The folder to get the list of files from
     * @return An array of paths for all the files in the folder and its subfolders
     */
    private static Path[] fileList(Path folder) {
        // Walk through the folder and get a list of all files
        try (Stream<Path> stream = Files.walk(folder)) {
            // Filter out directories and get the relative path of each actual file from the given root folder
            return stream.filter(Files::isRegularFile)
                         .toArray(Path[]::new);
        }
        catch (IOException e) {
            // If there was an error reading the files, log the error and return an empty array
            System.err.println(new Date() + " Error reading files: " + e);
            return new Path[0];
        }
    }

    /**
     * Get the content type of a file based on its extension
     * @param file The file to get the content type for
     * @return The content type of the file
     */
    private static String getContentType(Path file) {
        final String fileName = file.getFileName().toString();
        final String extension = fileName.substring(fileName.lastIndexOf('.') + 1);

        switch(extension) {
            case "html":
                return "text/html";
            case "css":
                return "text/css";
            case "js":
                return "text/javascript";
            case "png":
                return "image/png";
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "gif":
                return "image/gif";
            case "ico":
                return "image/x-icon";
            case "svg":
                return "image/svg+xml";
            default:
                return "text/plain";
        }
    }

    /**
     * Create a new HTTP server that serves files from the specified root folder. Additional handlers can be added
     * to the server to serve requests for specific paths. Call the start() method on the returned server to start
     * listening for requests.
     * 
     * @param port The port to listen on
     * @param rootFolder The folder to serve files from
     * @return The created HTTP server or null if there was an error creating the server
     * @throws IOException If there was an error creating the server
     */
    public static HttpServer buildFileServer(int port, String rootFolder) throws IOException {
        // Create a new HTTP server and set the file handler to serve files from the root folder
        final HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(null); // create a single-threaded server

        // Listen for requests to the root folder
        server.createContext("/", new FileServer(rootFolder));
        
        return server;
    }

    public static void main (String[] args) {
        // Set the default port to 8080 and the root folder to the public folder
        int port = 8080;
        String rootFolder = "./public";

        // Parse command line arguments
        for (String arg : args) {
            if (arg.startsWith("-p")) {
                // If the argument starts with -p, set the port to the number following the -p
                port = Integer.parseInt(arg.substring(2));
            }
            else if (arg.equals("-h") || arg.equals("--help")) {
                // If the argument is -h or --help, print the usage and exit
                System.out.println("Usage: java FileServer [-pPORT] [ROOT_FOLDER]");
                System.exit(0);
            }
            else if (new File(arg).isDirectory()) {
                // If the argument is a directory, set the root folder to the argument
                rootFolder = arg;
            }
        }

        // Create and start a new FileServer to handle file requests
        try {
            final HttpServer server = buildFileServer(port, rootFolder);
            server.start();
        }
        catch (IOException e) {
            System.err.println(new Date() + " Error starting server: " + e);
            System.exit(1); 
        }

        System.out.println(new Date() + " Server listening at http://localhost:" + port);
    }
}
