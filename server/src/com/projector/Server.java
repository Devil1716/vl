package com.projector;

import java.net.ServerSocket;
import java.net.Socket;
import java.io.InputStream;
import java.io.OutputStream;

public class Server {

    public static void main(String[] args) {
        System.out.println("Projector System Server Starting...");

        try {
            // Android uses a restricted app_process environment.
            // Looper.prepareMainLooper() is required for some internal components like MediaCodec async
            android.os.Looper.prepareMainLooper();
            
            int port = 8080;
            if (args.length > 0) {
                port = Integer.parseInt(args[0]);
            }

            ServerSocket serverSocket = new ServerSocket(port);
            System.out.println("Listening for PC connection on port " + port);

            Socket clientSocket = serverSocket.accept();
            System.out.println("PC Connected!");

            OutputStream videoOut = clientSocket.getOutputStream();
            InputStream controlIn = clientSocket.getInputStream();

            ScreenEncoder encoder = new ScreenEncoder(videoOut);
            Controller controller = new Controller(controlIn);

            Thread videoThread = new Thread(encoder::start);
            Thread controlThread = new Thread(controller::start);

            videoThread.start();
            controlThread.start();

            // Block forever
            android.os.Looper.loop();

        } catch (Exception e) {
            System.err.println("Server Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
