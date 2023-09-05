package webserver;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import db.DataBase;
import model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.IOUtils;

import static util.HttpRequestUtils.*;
import static util.HttpRequestUtils.parseQueryString;

public class RequestHandler extends Thread {
    private static final Logger log = LoggerFactory.getLogger(RequestHandler.class);

    private Socket connection;

    public RequestHandler(Socket connectionSocket) {
        this.connection = connectionSocket;
    }

    public void run() {
        log.debug("New Client Connect! Connected IP : {}, Port : {}", connection.getInetAddress(),
                connection.getPort());

        try (InputStream in = connection.getInputStream(); OutputStream out = connection.getOutputStream()) {
            // TODO 사용자 요청에 대한 처리는 이 곳에 구현하면 된다.
            DataOutputStream dos = new DataOutputStream(out);
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            StringBuilder sb = new StringBuilder();
            String line;
            int contentLength = 0;
            String contentBody = null;

            while ((line = br.readLine()) != null) {
                if ("".equals(line)) break;
                sb.append(line);
                sb.append("\n");
            }

            String requestHeader = sb.toString();
            String startLine = requestHeader.split("\n")[0];

            String[] startLineTokens = parseRequestHeaderStartLine(startLine);
            String requestMethod = startLineTokens[0];
            String requestURL = startLineTokens[1];


//            String requestURL = HttpRequestUtils.parseRequestUrl(HttpRequestUtils.parseRequestHeaderStartLine(startLine));
//            User user = getUserInfoByGET(requestURL);
//            if (user != null) {
//                System.out.println(user.toString());
//                return;
//            }

            contentLength = getContentLength(requestHeader);
            contentBody = IOUtils.readData(br, contentLength);

            if (requestURL.equals("/user/create")) {
                User user = getCreatedUserInfo(contentBody);
                DataBase.addUser(user);
                log.debug("User : {}", user);
//                requestURL = "/index.html";
                response302Header(dos);
            }


            if (requestURL.equals("/user/login")) {
                if (isLoginSuccess(contentBody)){
                    System.out.println("login success");
                }
            }

            File requestFile = getFile(getFullFilePath(requestURL));
            if (requestFile.isDirectory()) return;
            byte[] body = Files.readAllBytes(requestFile.toPath());

            response200Header(dos, body.length);
            responseBody(dos, body);
        } catch (
                IOException e) {
            log.error(e.getMessage());
        }

    }

    private void response302Header(DataOutputStream dos) {
        String location = "/index.html";
        try {
            dos.writeBytes("HTTP/1.1 302 Found \r\n");
            dos.writeBytes("Location: " + location);
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }


    private int getContentLength(String requestHeader) {
        String[] requestHeaderList = requestHeader.split("\n");

        for (String eachLine : requestHeaderList) {
            Pair headerMap = parseHeader(eachLine);
            if (headerMap == null) continue;
            if (headerMap.getKey().equals("Content-Length")) {
                return Integer.parseInt(headerMap.getValue());
            }
        }
        return 0;
    }

    private User getUserInfoByGET(String data) {
        int index = data.indexOf("?");
        if (index == -1) return null;

        String requestPath = data.substring(0, index);
        String params = data.substring(index + 1);
        return getCreatedUserInfo(params);
    }

    private User getCreatedUserInfo(String params) {
        Map<String, String> map = parseQueryString(params);
        return new User(map.get("userId"), map.get("password"), map.get("name"), map.get("email"));
    }

    private boolean isLoginSuccess(String params) {
        Map<String, String> map = parseQueryString(params);
        String userId = map.get("userId");
        String password = map.get("password");

        User user = DataBase.findUserById(userId);
        return user.getPassword().equals(password);
    }

    private void response200Header(DataOutputStream dos, int lengthOfBodyContent) {
        try {
            dos.writeBytes("HTTP/1.1 200 OK \r\n");
            dos.writeBytes("Content-Type: text/html;charset=utf-8\r\n");
            dos.writeBytes("Content-Length: " + lengthOfBodyContent + "\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void responseBody(DataOutputStream dos, byte[] body) {
        try {
            dos.write(body, 0, body.length);
            dos.flush();
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private String getFullFilePath(String requestUrl) {
        return "./webapp" + requestUrl;
    }

    private File getFile(String pathString) {
        Path path = Paths.get(pathString);
        return path.toFile();
    }
}
