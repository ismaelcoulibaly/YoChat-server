package server.handlers;

import static server.handlers.ServerStart.onlineUsers;
import static server.ui.serverForm.taConsole;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.HashSet;

import server.models.Paquet;
import server.models.User;
import server.utility.Command;
import static server.utility.utils.*;

/**
 * @author Josue Lubaki & Ismael Coulibaly
 * @version 1.0
 */
public class ClientHandler implements Runnable {

    private Socket clientSocket;
    private PrintWriter clientPrintWriter;
    private BufferedReader clientBufferedReader;

    public ClientHandler(Socket client, PrintWriter writer) {
        try {
            this.clientSocket = client;
            this.clientPrintWriter = writer;
            InputStreamReader inputStreamReader = new InputStreamReader(clientSocket.getInputStream());
            clientBufferedReader = new BufferedReader(inputStreamReader);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {

        String messageRecu;

        String[] messageSplit;
        try {

            while ((messageRecu = clientBufferedReader.readLine()) != null) {

                // taConsole.append("Received : " + messageRecu.replace("%%", " - ") + "\n");
                messageSplit = messageRecu.split("%%");

                String username = capitalize(messageSplit[0]);
                User user = new User(username);
                String message = messageSplit[1];
                String command = messageSplit[2];

                // Usernane : message : command
                Paquet paquet = new Paquet(user, message, command);
                Paquet paquetToSendClient = new Paquet(user, message, command);

                taConsole.append("Received : " + paquet.toString().replace("%%", " | ") + "\n");

                switch (paquet.getCommand()) {
                    case Command.CONNECT:
                        // enregistrement d'un nouvel utilisateur
                        // v??rifier si un User avec le meme username existe d??j??
                        boolean isExistUser = onlineUsers.keySet().stream()
                                .anyMatch(onlineUser -> onlineUser.getUsername().equals(user.getUsername()));

                        if (isExistUser) {
                            paquetToSendClient.setCommand(Command.SERVER_ERROR);
                            paquetToSendClient.setMessage(
                                    " L'Utilisateur avec comme username [" + user.getUsername() + "] existe d??j?? !");
                            user.setUsername("SERVEUR");
                            paquetToSendClient.setUser(user);
                            clientPrintWriter.println(paquetToSendClient);
                            clientPrintWriter.flush();

                            // reset username
                            user.setUsername(messageSplit[0]);
                            return;
                        }

                        // ajouter l'utilisateur ?? la liste des utilisateurs connect??s
                        onlineUsers.put(paquet.getUser(), clientPrintWriter);

                        // configurer le paquet avant de l'envoyer
                        paquet.setMessage(user.getUsername() + " vient de se connecter");
                        user.setUsername("SERVEUR");
                        paquet.setUser(user);
                        paquet.setCommand(Command.CHAT);

                        // Informer les autres d'un nouvel utilisateur
                        String usernameExcept = paquetToSendClient.getUser().getUsername();
                        notifyEveryClient(paquet.toString(), usernameExcept);

                        // faire un set du nom de l'utilisateur, r??cup??rer le nom de l'utilisateur sur
                        // le message que le server ??crit
                        user.setUsername(paquet.getMessage().split(" ")[0]);

                        // envoyer le message au client
                        paquetToSendClient.setMessage(user.getUsername() + " Connection R??ussi !");
                        clientPrintWriter.println(paquetToSendClient);
                        clientPrintWriter.flush();
                        break;

                    case Command.DISCONNECT:
                        // Supprimer l'utilisateur de la liste des utilisateurs connect??s
                        // l'utilisateur avec le nom user.getUsername()
                        onlineUsers.keySet().removeIf(user1 -> user1.getUsername().equals(user.getUsername()));

                        // configurer le paquet avant de l'envoyer
                        paquet.setMessage(user.getUsername() + " vient de se d??connecter ");
                        user.setUsername("SERVEUR");
                        paquet.setUser(user);
                        paquet.setCommand(Command.CHAT);

                        notifyEveryClient(paquet.toString(), null);

                        // faire un set du nom de l'utilisateur, r??cup??rer le nom de l'utilisateur sur
                        // le message que le server ??crit
                        user.setUsername(paquet.getMessage().split(" ")[0]);

                        // envoyer message au client
                        paquetToSendClient.setMessage("D??connection R??ussi !");
                        clientPrintWriter.println(paquetToSendClient);
                        clientPrintWriter.flush();

                        // fermer la connexion avec le socket du client
                        try {
                            Thread.sleep(1000);
                            clientSocket.close();
                            return;
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        break;

                    case Command.CHAT:
                        // V??rifier si le message contient @
                        if (!message.contains("@")) {
                            // Envoyer le message ?? tous les utilisateurs
                            notifyEveryClient(paquet.toString(), null);
                        } else {
                            // Faire un split du message contient @
                            String[] msgSplitWithArobase = message.split(" ");
                            HashSet<User> usersToSendMessage = new HashSet<>();
                            String usernameTosendMessage;
                            int i = 0;
                            while (true) {
                                if (!msgSplitWithArobase[i].contains("@")) {
                                    break;
                                } else {
                                    usernameTosendMessage = capitalize(msgSplitWithArobase[i].substring(1));
                                    usersToSendMessage.add(new User(usernameTosendMessage));

                                    int nbreCaractere = usernameTosendMessage.length() + 2; // @ + space = 2
                                    message = message.substring(nbreCaractere);
                                }
                                i++;
                            }

                            // update le message dans paquet
                            paquet.setMessage(message);

                            // Envoyer le message ?? tous les usersToSendMessage
                            usersToSendMessage.forEach(userToSend -> {
                                // v??rifier s'il est dans onlineUsers, si oui envoyer le message
                                for (User userOnline : onlineUsers.keySet()) {
                                    if (userOnline.equals(userToSend)) {
                                        onlineUsers.get(userOnline).println(paquet);
                                        onlineUsers.get(userOnline).flush();
                                    }
                                }
                            });

                            paquet.setMessage(messageSplit[1]);
                            // ??crire au client son message envoy??
                            clientPrintWriter.println(paquet);
                            clientPrintWriter.flush();
                        }
                        break;

                    case Command.LIST:
                        // configurer le paquet avant de l'envoyer
                        user.setUsername("SERVEUR");
                        paquetToSendClient.setUser(user);
                        paquetToSendClient.setCommand(Command.LIST);

                        StringBuilder msgToSend = new StringBuilder();
                        msgToSend.append(messageSplit[0])
                                .append(", voici la liste des utilisateurs actuellement ligne : ");

                        // configuer le message ?? envoyer au client demandant la liste
                        onlineUsers.keySet()
                                .forEach(userOnline -> msgToSend.append(" @").append(userOnline.getUsername()));

                        paquetToSendClient.setMessage(msgToSend.toString());
                        clientPrintWriter.println(paquetToSendClient);
                        clientPrintWriter.flush();

                        // reset username
                        user.setUsername(messageSplit[0]);
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

    /**
     * Envoyer un message ?? tous les utilisateurs connect??s
     * 
     * @param message          le message ?? envoyer
     * @param usernameExcepted le nom de l'utilisateur ?? exclure, sinon null
     */
    public static void notifyEveryClient(String message, String usernameExcepted) {
        try {
            // v??rifier si usernameExcepted est null
            if (usernameExcepted == null) {
                // envoyer le message ?? tous les utilisateurs
                onlineUsers.forEach((user, printWriter) -> {
                    printWriter.println(message);
                    printWriter.flush();
                });
            } else {
                // envoyer le message ?? tous les utilisateurs sauf usernameExcepted
                onlineUsers.forEach((user, printWriter) -> {
                    if (!user.getUsername().equals(usernameExcepted)) {
                        printWriter.println(message);
                        printWriter.flush();
                    }
                });
            }

            taConsole.setCaretPosition(taConsole.getDocument().getLength());
        } catch (Exception e) {
            taConsole.append("Erreur lors de l'envoi de la commande ?? tous les utilisateurs\n");
        }
    }
}