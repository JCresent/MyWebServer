import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.StringTokenizer;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

//Class representing the Web Server 
//handling opening sockets and sending http requests to parser and handler 
public class MyWebServer {
    //Vars for adding to headers (Server name and HTTP version)
    static final String HTTP_VERSION = "HTTP/1.1";
    static final String SERVER_NAME = "MyWebServer";

    //Var for marking incorrect date format - Error code 400
    static int wrongDate = 0; 
    
    // Entry point of the program
    public static void main(String[] args) throws IOException {

        int port; 
        String rootDir; 

        //Check to make sure server is opened with port number and root directory
        if(args.length != 2) {
            System.out.println("Error: Expected 2 arguements got "+Integer.toString(args.length));
            System.exit(0);
        }

        //args[0] = port number
        //args[1] = ~/evaluationWeb (path)
        port = Integer.parseInt(args[0]); 
        rootDir = args[1]; 
        

        //Create socket 
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("Web Server Started on port " + port);

        //Proccess HTTP requests
        while(true){
             //Create new client socket to send repsonses to the client 
             //Accept allows server socket to listen for a connection trying to be made by client
             MyWebServer.wrongDate = 0;
             Socket clientSocket = serverSocket.accept(); 
             handleReq(clientSocket, rootDir);
        }
    }

    //Method to bring in request strings coming from client requests and send them to be handled 
    private static void handleReq(Socket clientSocket, String rootDir) {

        //input -> read in client request string 
        //output -> format output stream to write to client
        try ( BufferedReader input = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
              DataOutputStream output = new DataOutputStream(clientSocket.getOutputStream()) ) 
        {
         /*STEPS FOR A REQUEST
          * 1. Read in and format reuqest string from user (getRequestString)
          * 2. Parse through the reqest string's info to fufill the GET or HEAD request (parseRequest)
          * 3. Server takes action on this request (handles or sends error message) (handleRequest)
          * 4. Repsonse is then sent to user saying if the repsonse was correctly handled or error
          */
         String requestString = getRequestString(input);
         HttpReq httpReq = parseRequest(requestString, rootDir);
         HttpResp httpResp = handleRequest(httpReq, rootDir);
         sendResponse(output, httpReq, httpResp, clientSocket);
         
        } catch (IOException e) {
            System.err.println(e.getMessage());
        } catch (ParseException e){
            System.err.println(e.getMessage());
        }
    }

    //Method to get request string 
    private static String getRequestString(BufferedReader input) throws IOException{
        StringBuilder requestBuilder = new StringBuilder();
        String line; 
        //Loop through read in lines to build request string 
        while((line = input.readLine()) != null && !line.isEmpty()) {
            requestBuilder.append(line).append("\r\n");
        }
        //Return the builder as a string 
        return requestBuilder.toString();
    }

    //Method setup Http request object aka the Request Parser 
    private static HttpReq parseRequest(String requestString, String rootDir){
        return new HttpReq(requestString, rootDir);
    }

    //Method to setup Http response object aka the Request Handler
    private static HttpResp handleRequest(HttpReq httpReq, String rootDir) throws FileNotFoundException, ParseException{
        return new HttpResp(httpReq, rootDir); 
    }

    //Method to send response to client after request has been parsed and handled by server 
    private static void sendResponse(DataOutputStream output, HttpReq httReq, HttpResp httpResp, Socket client) throws IOException{
        //Write to the client
        output.writeBytes(httpResp.toString());

        if("GET".equals(httReq.getMethod())) {
            if (httpResp.getBody() != null) output.write(httpResp.getBody().readAllBytes());
        }
        output.flush();
        client.close(); 
    }
}



/*OBJECT: Handles all Http requests incoming from the server (request parser)*/
final class HttpReq {
    //Important varaibles to store info for command(method), path and if-modified since date
    private String method; 
    private String path; 
    private Date ifModifiedSince; 

    
    //Constructor to parse through the http request string and tokenize it
    public HttpReq(String requestString, String rootDir){
        //Tokenizing the request string 
        StringTokenizer tokenizer = new StringTokenizer(requestString);
        //System.out.println("Request String: " + requestString);


        //Method - stores first token in all uppercase (GET or HEAD request)
        if (tokenizer.hasMoreTokens()) {
            method = tokenizer.nextToken().toUpperCase();
            //System.out.println("Command: " + method);
            }
        //Path - stores second token added onto input filepath + reqeusted path + index.html
        if (tokenizer.hasMoreTokens()) {
            String filePath = tokenizer.nextToken();
            //System.out.println(filePath);
            //Get rid of leading slash
            path = rootDir + filePath;
            if (path.endsWith("/")) {
                path += "index.html";
            }
            //System.out.println("Path: " + path);
        }
        //Loop through rest of message to find If-modified-since header string to store.
        //Formats date correctly as well to later compare with the last modified date in response
        while (tokenizer.hasMoreTokens()) {
            String header = tokenizer.nextToken();
            // System.out.println(header);
            if (header.equalsIgnoreCase("If-Modified-Since:")) {
                try {
                    StringBuilder dateStringBuild = new StringBuilder();
                    while(tokenizer.hasMoreTokens()){
                        String token = tokenizer.nextToken(); 
                        if (token.equals("2024")){
                            dateStringBuild.append(token);
                            break;
                        }
                        else{dateStringBuild.append(token);}
                        if (tokenizer.hasMoreTokens()) {
                            dateStringBuild.append(" ");
                        }
                    }
                    String dateString = dateStringBuild.toString(); 
                    // System.out.println("Header: " + header);
                    // System.out.println("Date: " + dateString);
                    String PATTERN = "EEE MMM dd HH:mm:ss zzz yyyy";
                    DateFormat formatter = new SimpleDateFormat(PATTERN);
                    ifModifiedSince = formatter.parse(dateString);
                    break;
                } catch (Exception e) {
                    //Handle parsing exception
                    ifModifiedSince = null;
                    MyWebServer.wrongDate = 1;
                }
            } 
        }

    }

    //GETTER METHODS: Used in Request Handler

    //Getter method to return the command input by client - code only implements functionality for GET and HEAD
    public String getMethod() {
        return method;
    }

    //Getter method to return path 
    public String getPath() {
        return path;
    }

    //Getter method to return the date since last modified 
    public Date getIfModifiedSince() {
        return ifModifiedSince;
    }

}




/*OBJECT: Handles all Http responses (request handler) */
final class HttpResp {

    //Variables to write repsonse 
    private int statusCode;
    private String statusMessage;
    private String date;
    private String server;
    private String lastModified;
    private long contentLength;
    private FileInputStream responseBody;

    //Constructor to taken in parsed request and root directory to format if request is valid and can be handled 
    // or if an error message needs to be sent to the user 
    public HttpResp(HttpReq httpReq, String rootDir) throws FileNotFoundException, ParseException {
        this.date = new Date().toString();
        this.date = date.replace("EDT", "EST");
        this.server = MyWebServer.SERVER_NAME;

        String method = httpReq.getMethod();
        if ("GET".equals(method)) {
            File file = new File(httpReq.getPath());
            //System.out.println("File:"+file);

            if(MyWebServer.wrongDate == 1){
                this.statusCode = 400;
                this.statusMessage = "Bad Request";
                // this.responseBody = "<h1>404 Not Found</h1>";
                return;
            }
            
            //Check if the requested file exists
            if (!file.exists() || !file.isFile()) {
                this.statusCode = 404;
                this.statusMessage = "Not Found";
                //System.out.println("File doesn't exist!");
                //this.responseBody = "<h1>404 Not Found</h1>";
                return;
            }

            //File exists check its length and when it was modified 
            this.contentLength = file.length(); 
            this.lastModified = formatDate(file.lastModified()); 
            this.lastModified = lastModified.replace("EDT", "EST");

           //Check if If-Modified-Since header is provided and file modification date is after that date (Might only need for HEAD)
           Date ifModifiedSince = httpReq.getIfModifiedSince();
           Date lastModifiedDate = StrToDate(lastModified);
           // System.out.println(ifModifiedSince);
           // System.out.println(lastModifiedDate);
           //System.out.println("Has file been modified since: "+ifModifiedSince.getTime());
           if (ifModifiedSince != null) {
               int comparison = ifModifiedSince.compareTo(lastModifiedDate);
               if (comparison > 0)
               {
                   this.statusCode = 304;
                   this.statusMessage = "Not Modified";
                   return;
               }
           }
            //File exists and needs to be sent
            this.statusCode = 200;
            this.statusMessage = "OK";
            //Get object file (path) and store it to the body
            FileInputStream object = new FileInputStream(file.toString());
            this.responseBody = object;
            return;
        } 
        if ("HEAD".equals(method)) {
            //Similar logic as GET but without sending the file content (body)
            //Set appropriate status code and message for HEAD request
            File file = new File(httpReq.getPath());
            //System.out.println(file.toString());

            if(MyWebServer.wrongDate == 1){
                this.statusCode = 400;
                this.statusMessage = "Bad Request";
                // this.responseBody = "<h1>404 Not Found</h1>";
                return;
            }
            
            //Check if the requested file exists
            if (!file.exists() || !file.isFile()) {
                //if (!file.exists() && !file.isFile()) System.out.println("Both false??"); 
                this.statusCode = 404;
                this.statusMessage = "Not Found";
                // this.responseBody = "<h1>404 Not Found</h1>";
                return;
            }

            //File exists check its length and when it was modified 
            this.contentLength = file.length(); 
            this.lastModified = formatDate(file.lastModified());
            this.lastModified = lastModified.replace("EDT", "EST");
            //System.out.println(lastModified);

            //Check if If-Modified-Since header is provided and file modification date is after that date
            Date ifModifiedSince = httpReq.getIfModifiedSince();
            Date lastModifiedDate = StrToDate(lastModified);
            // System.out.println(ifModifiedSince);
            // System.out.println(lastModifiedDate);
            //System.out.println("Has file been modified since: "+ifModifiedSince.getTime());
            if (ifModifiedSince != null) {
                int comparison = ifModifiedSince.compareTo(lastModifiedDate);
                if (comparison > 0)
                {
                    this.statusCode = 304;
                    this.statusMessage = "Not Modified";
                    return;
                }
            }
            //File exists and needs to be sent
            this.statusCode = 200;
            this.statusMessage = "OK";
            return;
        }
        //Unsupported method, set 501 Not Implemented - Didn't match GET or HEAD (Ex: POST)
        this.statusCode = 501;
        this.statusMessage = "Not Implemented";
        return; 
    }

    //Helper method to format the lastModifed date 
    private String formatDate(long timestamp){
        SimpleDateFormat PATTERN = new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy");
        Date date = new Date(timestamp);
        String formattedDate = PATTERN.format(date);
        return formattedDate;
    }

    //Helper method to convert lastModifed date string into a date for comparison with If-Modified-Since
    private Date StrToDate(String lastModifed) throws ParseException{
        String dateString = lastModified;
        SimpleDateFormat PATTERN = new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy");
        Date date = PATTERN.parse(dateString);
        return date; 
    }

    @Override
    public String toString() {
        StringBuilder responseBuilder = new StringBuilder();
        //Build HTTP/1.1 <CODE> and status message - request line
        responseBuilder.append(MyWebServer.HTTP_VERSION).append(" ").append(statusCode).append(" ").append(statusMessage).append("\r\n");

        //Check to make sure we need headers (only for OK status)
        if(this.statusCode != 404 && this.statusCode != 304 && this.statusCode != 501 && this.statusCode != 400) {
        //Headers
            responseBuilder.append("Date: ").append(date).append("\r\n");
            responseBuilder.append("Server: ").append(server).append("\r\n");
            responseBuilder.append("Last-Modified: ").append(lastModified).append("\r\n");
            responseBuilder.append("Content-Length: ").append(contentLength).append("\r\n");
            responseBuilder.append("\r\n");
        }
        else if(this.statusCode == 304){
            responseBuilder.append("Date: ").append(date).append("\r\n");
            responseBuilder.append("\r\n");
            responseBuilder.append("Error 304: Not modified");
        }
        else if(this.statusCode == 404){
            responseBuilder.append("Date: ").append(date).append("\r\n");
            responseBuilder.append("Server: ").append(server).append("\r\n");
            responseBuilder.append("\r\n");
            responseBuilder.append("Error 404: File not found");
        }
        else if(this.statusCode == 501){
            responseBuilder.append("\r\n");
            responseBuilder.append(MyWebServer.HTTP_VERSION).append(" ").append(statusCode).append(" ").append(statusMessage).append("\r\n");
        }
        else if(this.statusCode == 400){
            responseBuilder.append("\r\n");
            responseBuilder.append("Error 400: Bad request");
        }
        return responseBuilder.toString();
    }

    //Method to help build message body for GET reqeusts 
    public FileInputStream getBody(){
        return responseBody; 
    }

}
