/**
 * Author: Haleigh Walker
 * File: server.MemD
 */

package server;

import java.util.*;
import java.io.*;
import java.net.*;

public class MemD {
	
    public static void main(String[] args) {
		int tcp_port = 0;
		//Check command arguments
		if (args.length != 1) {
			System.out.println("Invalid number of args, exiting...");
			System.exit(1);
		}
		try {
			tcp_port = Integer.parseInt(args[0]);
		} 
		catch (Exception e) {
			System.out.println("Could not convert passed in tcp port '" + args[0] + "' to an integer, exiting...");
			System.exit(2);
		}
		//Creating server on TCP port
		Server s = new Server(tcp_port);
		s.start();		
	}
}

//Shutdown hook for Ctrl-C to close sockets
class Shutdown extends Thread {
	private ServerSocket s;
	private ArrayList<Socket> socket_list;
	
	public Shutdown(ServerSocket s) {
		this.s = s;
		socket_list = new ArrayList<Socket>();
	}
	
	public void addSocket(Socket socket) {
		socket_list.add(socket);
	}
	
	public void run() {
		try {
			System.out.println("Exiting down...");
			Thread.sleep(200);
			for (int x = 0; x < socket_list.size(); x++) {
				socket_list.get(x).close();
			}
		} 
		catch (Exception e) {
			e.printStackTrace();
		}
	}
}

//Create and maintain MemberList
class MemberList {
	private static MemberList singleton;
	private Vector<Member> member_list;
	
	private MemberList() {
		member_list = new Vector<Member>();
	}
	
	//Get MemberList
	public synchronized static MemberList get() {
		if (singleton == null) {
			singleton = new MemberList();
		}
		return(singleton);
	}
	
	//Add member to the member list
	public synchronized void add(Member m) {
		member_list.add(m);
	}
	
	//Remove member from the memebr list
	public synchronized void remove(String screen_name) {
		for (int x = 0; x < member_list.size(); x++) {
			if (member_list.get(x).getScreenName().equals(screen_name)) {
				member_list.remove(x);
			}
		}
	}
	
	//Get member list
	public synchronized Vector<Member> getMemberList() {
		return (member_list);
	}
}
	
//Creating a Member
class Member {
	private String screen_name;
	private String ip_address;
	private String udp_port;
	
	public Member(String screen_name, String ip_address, String udp_port) {
		this.screen_name = screen_name;
		this.ip_address = ip_address.trim();
		this.udp_port = udp_port;
	}
	
	//Get member screen name
	public String getScreenName() {
		return (screen_name);
	}
	
	//Get member IP address
	public String getIPAddress() {
		return (ip_address);
	}
	
	//Get member Inet address
	public InetAddress getInetAddress() {
		InetAddress temp_addr = null;
		try {
			temp_addr = InetAddress.getByName(ip_address);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		return (temp_addr);
	}
	
	//Get member UDP port
	public int getUDPPort() {
		return (Integer.parseInt(udp_port));
	}
}

//Starting server and connecting to clients
class Server {
	private int tcp_port;
	
	public Server(int tcp_port) {
		this.tcp_port = tcp_port;
	}
	
	public void start() {
		ServerSocket welcome_socket = null;
		Socket tcp_welcome = null;
		try {
			welcome_socket = new ServerSocket(tcp_port); 
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		
		Shutdown s = new Shutdown(welcome_socket);
		Runtime.getRuntime().addShutdownHook(s);
		
		boolean running = true;
		int process_count = 0;
	    while(running) {
			try {
				tcp_welcome = welcome_socket.accept();
				s.addSocket(tcp_welcome);
				process_count++;
				//Create client threads
				new ClientServant(tcp_welcome).start();
			}
			catch (Exception e) {
				e.printStackTrace();
			}
		}
		//Close TCP socket
		try {
			welcome_socket.close();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
}

//Thread for handling the client 
class ClientServant extends Thread {
	private Socket tcp_socket;
	private DatagramSocket udp_socket;
	private MemberList member_list;
	private String screen_name = "";
	private String ip_address = "";
	private String udp_port = "";
	
	public ClientServant(Socket tcp_socket) {
			this.tcp_socket = tcp_socket;
			//Get MemberList
			member_list = MemberList.get();
			try {
				//Create UDP socket
				udp_socket = new DatagramSocket();
			}
			catch (Exception e) {
				e.printStackTrace();
			}
	}
	
	public void run() {
		boolean running = true;
		while (running) {
			BufferedReader tcp_in = null;
			String tcp_response = "";
			try {
				tcp_in = new BufferedReader(new InputStreamReader(tcp_socket.getInputStream()));
				tcp_response = tcp_in.readLine();
			}
			catch (Exception e) {
				e.printStackTrace();
			}
			//Handle HELO protocol
			if (tcp_response.startsWith("HELO")) {
				String [] splits = tcp_response.split(" ");
				String protocol = splits[0];
				screen_name = splits[1];
				ip_address = splits[2];
				udp_port = splits[3];
				//Accept client
				if (!checkForName(screen_name)) {
					member_list.add(new Member(screen_name, ip_address, udp_port));
					String acpt_message = "ACPT ";
					Vector<Member> temp_mlist = member_list.getMemberList();
					for (int x = 0; x < temp_mlist.size(); x++) {
						Member temp_m = temp_mlist.get(x);
						acpt_message += temp_m.getScreenName() + " " + temp_m.getIPAddress() + " " + temp_m.getUDPPort() + ":";
					}
					acpt_message = acpt_message.substring(0, acpt_message.length() - 1);
					sendTCP(acpt_message);
					//Send JOIN protocol
					String join_message = "JOIN " + screen_name + " " + ip_address + " " + udp_port + "\n";
					sendUDP(join_message);
				}
				//Reject client
				else {
					String rjct_message = "RJCT "+ screen_name;
					sendTCP(rjct_message);
					running = false;
					continue;
				}
			}
			//Handle EXIT protocol
			else if (tcp_response.startsWith("EXIT")) {
					String exit_message = "EXIT " + screen_name + "\n";
					sendUDP(exit_message);
					member_list.remove(screen_name);
					running = false;
					continue;
			}
			else {
				System.out.println("Invalid command from the client");
			}
		}
		//Close UDP socket
		try {
			udp_socket.close();
			tcp_socket.close();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	//Send UDP to clients
	public void sendUDP(String message_udp) {
		try {
			Vector<Member> temp_mlist = member_list.getMemberList();
			for (int x = 0; x < temp_mlist.size(); x++) {
				byte [] send_data = new byte[1024];
				send_data = message_udp.getBytes();
				Member m = temp_mlist.get(x);
				DatagramPacket send_packet = new DatagramPacket(send_data, send_data.length, m.getInetAddress(), m.getUDPPort());  
				udp_socket.send(send_packet);
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	//Send TCP to client
	public void sendTCP(String message_tcp) {
		try {
			DataOutputStream tcp_out = new DataOutputStream(tcp_socket.getOutputStream());
			tcp_out.writeBytes(message_tcp + '\n');
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	//Check for member name
	public boolean checkForName(String sname) {
		boolean ret = false;
		Vector<Member> temp_mlist = member_list.getMemberList();
		for (int x = 0; x < temp_mlist.size(); x++) {
			Member m = temp_mlist.get(x);
			if  (m.getScreenName().equals(sname)) {
				ret = true;
				break;
			}
		}
		return (ret);
	}
}
