package yochat.server.handlers;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

import yochat.server.models.Command;
import yochat.server.models.Paquet;
import yochat.server.models.User;
import static yochat.server.ui.serverForm.*;
import static yochat.server.handlers.ServerStart.onlineUsers;

public class ClientHandler implements Runnable {

    private Socket clientSocket;
    private PrintWriter clientPrintWriter;
    private BufferedReader clientBufferedReader;

    public ClientHandler(Socket client, PrintWriter writer) {
        try {
            this.clientSocket = client;
            InputStreamReader inputStreamReader = new InputStreamReader(clientSocket.getInputStream());
            clientBufferedReader = new BufferedReader(inputStreamReader);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {

        String message;
        String[] data;
        try {

            while ((message = clientBufferedReader.readLine()) != null) {
                taConsole.append("Received : " + message + "\n");
                data = message.split(":");

                User user = new User(data[0]);

                // Usernane : message : command
                Paquet paquet = new Paquet(user, data[1], data[2]);

                switch (paquet.getCommand()) {
                case Command.CONNECT:
                    // enregistrement d'un nouvel utilisateur
                    onlineUsers.put(user, clientPrintWriter);

                    // configurer le paquet avant de l'envoyer
                    paquet.setCommand(Command.CHAT);
                    paquet.setMessage("vient de se connecter ");

                    // Informer les autres d'un nouvel utilisateur
                    tellEveryOne(paquet);

                    // Informer l'utilisateur de la connexion

                    clientPrintWriter.println(paquet);
                    clientPrintWriter.flush();
                    break;

                default:
                    System.out.println("Commande inconnue");
                    break;
                }

            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void tellEveryOne(Paquet paquet) {
        try {
            for (PrintWriter writer : onlineUsers.values()) {
                writer.println(paquet.getUser().getUsername() + ":" + paquet.getMessage() + ":" + paquet.getCommand());
                writer.flush();
                taConsole.setCaretPosition(taConsole.getDocument().getLength());
            }
        } catch (Exception e) {
            taConsole.append("Erreur lors de l'envoi de la commande à tous les utilisateurs\n");
        }
    }
}