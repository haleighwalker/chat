/**
 * Author: Haleigh Walker
 * File: client.Chatter
 */
package client;

import java.util.*;
import java.io.*;
import java.net.*;

public class Chatter {
	
    public static void main(String[] args) {
		String screen_name = args[0];
		String server_addr = args[1];
		int port = Integer.parseInt(args[2]);
		
		// Create and start client
		Client client = new Client(screen_name, server_addr, port);
		int ret = client.start();
	}
}

class Client {
	private String screen_name;
	private String server_addr;
	private int port;
	private DatagramSocket udp_socket = null;
	private Socket tcp_socket = null;
	private DataOutputStream tcp_out = null;
	private BufferedReader tcp_in = null;
	private ArrayList<Member> member_list = new ArrayList<Member>();
	private ListenUDP udp_obj = null;
	private ListenInput input_obj = null;
	
	public Client(String screen_name, String server_addr, int port) {
		this.screen_name = screen_name;
		this.server_addr = server_addr;
		this.port = port;
	}
	
	public int start() {
		try {
			
			// TCP socket setup
			tcp_socket = new Socket(server_addr, port);
			String client_ip = tcp_socket.getLocalAddress().toString();
			client_ip = client_ip.replace("/", "");
			tcp_out = new DataOutputStream(tcp_socket.getOutputStream());
			tcp_in = new BufferedReader(new InputStreamReader(tcp_socket.getInputStream()));
			String message_tcp = "";
			String response_tcp = "";			
			
			// UDP socket setup
			udp_socket = new DatagramSocket();
			int udp_port = udp_socket.getLocalPort();
			
			// Send HELO protocol to the server
			message_tcp = "HELO " + screen_name + " " + client_ip + " " + udp_port;
			tcp_out.writeBytes(message_tcp + '\n');
			
			// Receive ACPT/RJCT response from server
			response_tcp = tcp_in.readLine();
			if (response_tcp.startsWith("RJCT")) { 
				System.out.println("Screen name already exists: " + screen_name + "\nExiting...");
				return(1); 
			}
			else if (response_tcp.startsWith("ACPT")) { 
				System.out.println("Welcome to the chat " + screen_name + "!");
				System.out.println("My port: " + udp_port);
				response_tcp = response_tcp.replace("ACPT ", "");
				
				// Create member list
				String [] response_splits = response_tcp.split(":");
				for (int x = 0; x < response_splits.length; x++) {
					String [] temp_splits = response_splits[x].split(" ");
					String sname = temp_splits[0];
					String iaddress = temp_splits[1];
					String uport = temp_splits[2];
					if (!sname.equals(screen_name)) {
						System.out.println(sname + " is in the chatroom");
						Member m = new Member(sname, iaddress, uport);
						member_list.add(m);
					}
				}
			}
			else {
				System.out.println("Unknown response from server, exiting...");
				return(1);
			}
			
			// Set up objects to listen on UDP and listen for user input
			udp_obj = new ListenUDP(this);
			input_obj = new ListenInput(this);

			udp_obj.start();
			input_obj.start();
			
			udp_obj.join();
			input_obj.join();
			
			udp_socket.close();
			tcp_socket.close();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		return(0);
	}
	
	public DatagramSocket getUDPSocket() {
		return(udp_socket);
	}
	
	public String getScreenName() {
		return(screen_name);
	}
	
	public ListenUDP getUDPObj() {
		return(udp_obj);
	}
	
	public ArrayList<Member> getMemberList() {
		return(member_list);
	}
	
	public void sendTCP(String message_tcp) {
		try {
			tcp_out.writeBytes(message_tcp + '\n');
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
}

class Member {
	private String screen_name;
	private String ip_address;
	private String udp_port;
	
	public Member(String screen_name, String ip_address, String udp_port) {
		this.screen_name = screen_name;
		this.ip_address = ip_address.trim();
		this.udp_port = udp_port;
	}
	
	public String getScreenName() {
		return (screen_name);
	}
	
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
	
	public int getUDPPort() {
		return (Integer.parseInt(udp_port));
	}
}

class ListenUDP extends Thread {
	DatagramSocket udp_socket;
	Client client_obj;
	ArrayList<Member> member_list;
	String screen_name;
	
	public ListenUDP(Client client_obj) {
		this.client_obj = client_obj;
		udp_socket = this.client_obj.getUDPSocket();
		member_list = this.client_obj.getMemberList();
		screen_name = this.client_obj.getScreenName();
	}
	
	public void run() {
		boolean running = true;
		try { 
			while (running) {
				// Create byte array to receive the max size of a UDP packet
				byte[] receive_data = new byte[65527];
				DatagramPacket receive_packet = new DatagramPacket(receive_data, receive_data.length); 
				udp_socket.receive(receive_packet); 
				String packet = new String(receive_packet.getData());
				packet = packet.trim();
				
				// Listen for JOIN protocol from server
				if (packet.startsWith("JOIN")) {
					String [] temp_splits = packet.split(" ");
					String protocol = temp_splits[0];
					String sname = temp_splits[1];
					String iaddress = temp_splits[2];
					String uport = temp_splits[3];
					if (!screen_name.equals(sname)) {
						System.out.println(sname + " has joined the chat!");
						Member m = new Member(sname, iaddress, uport);
						member_list.add(m);
					}
				}
				// Listen for MESG from other clients
				else if (packet.startsWith("MESG")) {
					String [] temp_splits = packet.split(" ");
					String protocol = temp_splits[0];
					String send_sname = temp_splits[1];
					String msg = "";
					if(temp_splits.length >= 3) {
						for(int x = 2; x < temp_splits.length; x++) {
							msg = msg + " " + temp_splits[x];
						}
					}
					System.out.println(send_sname + msg);
				}
				// Listen for EXIT protocol from server
				else if (packet.startsWith("EXIT")) {
					String [] temp_splits = packet.split(" ");
					String protocol = temp_splits[0];
					String sname = temp_splits[1];
					if (sname.equals(screen_name)) {
						System.out.println("Goodbye!");
						running = false;
						continue;
					}
					// Notify chat members that a client has left the chat
					else {
						System.out.println(sname + " has left the chat!");
					}
					// Upate member list
					for (int x = 0; x < member_list.size(); x++) {
						if (member_list.get(x).getScreenName().equals(sname)) {
							member_list.remove(x);
						}
					}
				}
				else {
					System.out.println("Unexpected response from sender: " + packet);
				}
			}
		} 
		catch (Exception e) { 
			e.printStackTrace(); 
		} 
	}
	
	public void sendUDP(String message) {
		try {
			message = "MESG " + screen_name + ": " + message + '\n';
			for (int x = 0; x < member_list.size(); x++) {
				byte [] send_data = new byte[1024];
				send_data = message.getBytes();
				Member m = member_list.get(x);
				DatagramPacket send_packet = new DatagramPacket(send_data, send_data.length, m.getInetAddress(), m.getUDPPort());  
				udp_socket.send(send_packet);
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
}

class ListenInput extends Thread {
	Client client_obj = null;

	public ListenInput(Client client_obj) {
		this.client_obj = client_obj;
	}
	
	public void run() {
		boolean running = true;
		BufferedReader user_input = new BufferedReader(new InputStreamReader(System.in));
		while (running) {
			try { 
				System.out.print(client_obj.getScreenName() + ": ");
				String input = user_input.readLine();
				// Capture EOF
				if (input == null) {
					running = false;
					String exit_message = "EXIT";
					client_obj.sendTCP(exit_message);
					continue;
				}
				// Handle empty messages
				if (input.equals("")) {
					input = " ";
				}
				client_obj.getUDPObj().sendUDP(input);
			} 
			catch (Exception e) { 
				e.printStackTrace(); 
			} 
		}
	}
}