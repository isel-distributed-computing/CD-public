package demospread;

import spread.SpreadException;

import java.net.UnknownHostException;
import java.util.List;
import java.util.Scanner;

public class DemoSpread {

    private static final int DAEMON_PORT=4803;

    public static void main(String[] args) {
        // IP do daemon: 1.º argumento, ou localhost por omissão
        String daemonIP = args.length > 0 ? args[0] : "localhost";
        // Scanner sobre System.in
        Scanner scaninput = new Scanner(System.in);
        String userName = read("Member's unique name? ", scaninput);

        GroupMember member;
        try {
            member = new GroupMember(userName, daemonIP, DAEMON_PORT);
        } catch (UnknownHostException e) {
            System.err.println("Can't find the daemon " + daemonIP);
            System.exit(1);
            return;
        } catch (SpreadException e) {
            System.err.println("There was an error connecting to the daemon " + daemonIP + ": " + e.getMessage());
            System.exit(1);
            return;
        }
        System.out.println("connected to "+daemonIP);
        System.out.println("Main ThreadID="+Thread.currentThread().threadId());

        try {
            boolean end=false;
            while (!end) {
                int option = Menu(scaninput);
                // um erro numa opção (ex.: nome de grupo inválido) não termina o programa
                try {
                    switch (option) {
                        case 1:  // join group
                            String groupName = read("Join to group named? ", scaninput);
                            if (!member.JoinToGrupo(groupName))
                                System.out.println("Already joined to " + groupName + ".");
                            break;
                        case 2: // send message
                            String groupToSend=read("Group Name to send Message? ", scaninput);
                            String txtMessage=read("Message text (Data)? ",scaninput);
                            member.SendMessage(groupToSend,txtMessage);
                            break;
                        case 3: // send N messages to Group
                            String groupToSendNmsg=read("Group Name to send Message? ", scaninput);
                            int N=10;
                            for (int i=1; i <= N; i++)
                                 member.SendMessage(groupToSendNmsg,"Msg "+i+" de "+N);
                            break;
                        case 4:
                            List<String> groupsBelonging=member.getNamesOfBelongingGroups();
                            // Leave a group.
                            if (groupsBelonging.isEmpty()) {
                                System.out.println("No group to leave.");
                                break;
                            }
                            for (String gname : groupsBelonging)
                                System.out.println("Joined to: " + gname + ".");
                            String nameToLeave=read("Group name to leave? ",scaninput);
                            if (member.groupLeave(nameToLeave))
                                System.out.println("Left from " + nameToLeave + ".");
                            else
                                System.out.println("Not joined to " + nameToLeave + ".");
                            break;
                        case 5:
                            String privateNameDestination=read("Member to send unicast message? ",scaninput);
                            String daemonNameDestination=read("Daemon name of that member (to format the destination private group)? ", scaninput);
                            String msgUnicast=read("Unicast message text (use \"privateRequest\" to obtain a reply)? ",scaninput);
                            member.sendUnicastMessage(privateNameDestination, daemonNameDestination, msgUnicast);
                            break;
                        case 99:
                            end = true;
                            break;
                        default:
                            System.out.println("Invalid option.");
                    }
                } catch (SpreadException e) {
                    System.err.println("Spread error: " + e.getMessage());
                }
            }
        } finally {
            // fecha a ligação mesmo que o ciclo termine com uma exceção
            try {
                member.close();
            } catch (SpreadException e) {
                System.err.println("Error closing the connection: " + e.getMessage());
            }
        }
        System.exit(0);
    }

    // devolve a opção escolhida; as opções inválidas são tratadas no default do switch
    private  static int Menu(Scanner scaninput) {
        System.out.println();
        System.out.println("    MENU");
        System.out.println(" 1 - Join to a Group");
        System.out.println(" 2 - Send Message to Group");
        System.out.println(" 3 - Send N messages to group");
        System.out.println(" 4 - Leave from a Group");
        System.out.println(" 5 - Send message unicast to a member");
        System.out.println("99 - Exit");
        System.out.println();
        System.out.println("Choose an Option?");
        if (!scaninput.hasNextLine()) {
            return 99;   // fim do input (ex.: Ctrl-D): sair
        }
        // ler a linha inteira: nextInt() deixaria o '\n' por consumir
        // e falharia com InputMismatchException se não fosse um número
        try {
            return Integer.parseInt(scaninput.nextLine().trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String read(String msg, Scanner scaninput) {
        System.out.println(msg);
        return scaninput.nextLine();
    }


}
