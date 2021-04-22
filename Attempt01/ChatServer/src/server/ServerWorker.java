package server;

import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;


public class ServerWorker extends Thread{

    private final Socket clientSocket;
    private final Server server;
    private String login = null;
    private OutputStream outputStream;
    private HashSet<String> topicSet = new HashSet<>();

    public ServerWorker(Server server, Socket clientSocket) {
        this.server = server;
        this.clientSocket = clientSocket;
    }

    public String getLogin(){
        return login;
    }

    @Override
    public void run() {
        try {
            handleClientSocket();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    public void handleClientSocket() throws IOException, InterruptedException {
        InputStream inputStream = clientSocket.getInputStream();
        this.outputStream = clientSocket.getOutputStream();

        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String line;
        while((line = reader.readLine()) != null){
            String[] tokens = StringUtils.split(line);
            if(tokens != null && tokens.length > 0){
                String cmd = tokens[0];
                if("logoff".equalsIgnoreCase(cmd) || "quit".equalsIgnoreCase(cmd)){
                    handleLogoff();
                    break;
                }else if("login".equalsIgnoreCase(cmd)){
                    handleLogin(outputStream, tokens);
                }else if ("msg".equalsIgnoreCase(cmd)){
                    String[] tokensMsg = StringUtils.split(line, null, 3);
                    handleMessage(tokensMsg);
                }else if ("join".equalsIgnoreCase(cmd)){
                    handleJoin(tokens);
                }else if ("leave".equalsIgnoreCase(cmd)){
                    handleLeave(tokens);
                }

                else{
                    String msg = "Unknown " + cmd + "\n";
                    outputStream.write(msg.getBytes());
                }
            }
        }
        clientSocket.close();
    }

    private void handleLeave(String[] tokens) {
        if(tokens.length > 1){
            String topic = tokens[1];
            topicSet.remove(topic);
        }
    }

    public boolean isMemberOfTopic(String topic){
        return topicSet.contains(topic);
    }

    private void handleJoin(String[] tokens) {
        if(tokens.length > 1){
            String topic = tokens[1];
            topicSet.add(topic);
        }
    }

    //format: "msg "login" body...
    //format: "msg" "#topic" body...
    private void handleMessage(String[] tokens) throws IOException{
        String sendTo = tokens[1];
        String body = tokens[2];

        boolean isTopic = sendTo.charAt(0) == '#';

        List<ServerWorker> workerList = server.getWorkerList();
        for(ServerWorker worker: workerList){
            if(isTopic) {
                if(worker.isMemberOfTopic(sendTo)){
                    String outMsg = "msg " + sendTo + ":" +  login + " " + body + "\n";
                    worker.send(outMsg);
                }
            }else{
                if(sendTo.equalsIgnoreCase(worker.getLogin())){
                    String outMsg = "msg " + login + " " + body + "\n";
                    worker.send(outMsg);
                }
            }
        }
    }

    private void handleLogoff() throws IOException {
        server.removeWorker(this);
        List<ServerWorker> workerList = server.getWorkerList();

        //send other online users current user's status
        String onlineMsg = "offline " + login + "\n";
        for(ServerWorker worker : workerList){
            if(!login.equals(worker.getLogin())) {
                worker.send(onlineMsg);
            }
        }
        clientSocket.close();

    }

    private void handleLogin(OutputStream outputStream, String[] tokens) throws IOException {
        if(tokens.length == 3) {
            String login = tokens[1];
            String password = tokens[2];

            if((login.equals("guest") && password.equals("guest")) || (login.equals("thoni") && password.equals("thoni"))  ){
                String msg = "\nok login\n\n";
                outputStream.write(msg.getBytes());
                this.login = login;
                System.out.println("User '" + login +"' logged in ");

                List<ServerWorker> workerList = server.getWorkerList();

                //send current user all other online login's
                for(ServerWorker worker: workerList){
                    if(!login.equals(worker.getLogin())) {
                        if(worker.getLogin() != null) {
                            String msg2 = "online " + worker.getLogin() + "\n";
                            send(msg2);
                        }
                    }
                }

                //send other online users current user's status
                String onlineMsg = "Online " + login + "\n";
                for(ServerWorker worker : workerList){
                    if(!login.equals(worker.getLogin())) {
                        worker.send(onlineMsg);
                    }
                }
            }else{
                String msg = "error login\n";
                outputStream.write(msg.getBytes());
            }
        }else{
            String msg = "Missing arguments (user password)\n";
            outputStream.write(msg.getBytes());
        }
    }

    private void send(String onlineMsg) throws IOException {
        if(login != null) {
            outputStream.write(onlineMsg.getBytes());
        }
    }
}
