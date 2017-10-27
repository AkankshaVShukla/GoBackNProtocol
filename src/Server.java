import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// Class for the central server
public class Server {

	public static void main(String[] args) throws IOException {

		int port = 0;
		String filename = "";
		double probability = 0;
		if(args==null || args.length!=3){
			System.out.println("Invalid input");
		}
		if (args != null && args.length > 0) {

			port = Integer.parseInt(args[0]);

			if (args.length > 1) {
				filename = args[1];
			}

			if (args.length > 2) {
				probability = Double.parseDouble(args[2]);
			}
		}
		System.out.println("Server started . . .");
		new ClientListner(7735, "test.pdf", 0.05).start();
	}

	/*
	 * Class to listen to new client requests
	 */
	private static class ClientListner extends Thread {
		int port;
		String filename;
		double probability;
		
		int expectedSequenceNumber;
		
		List<FTPPacket> bufferFTPPackets = new ArrayList<FTPPacket>();
		
		final int bufflength = 1024; // To be verified

		protected DatagramSocket socket = null;

		public ClientListner(int port, String filename, double probability) throws SocketException {

			this.port = port;
			this.filename = filename;
			this.probability = probability;
			this.expectedSequenceNumber = 0;
			
			// Create a new Datagram socket
			if (this.port > 0)
				socket = new DatagramSocket(this.port);

		}

		public void run() {			
			if (socket != null) {
				while (true) {
					boolean packetLoss 	= false;
					
					byte[] buffer = new byte[bufflength];

					DatagramPacket packet = new DatagramPacket(buffer, bufflength);
					try {						
						socket.receive(packet);
						
						byte[] udpPacket = packet.getData();
						
						ByteArrayInputStream bin = new ByteArrayInputStream(udpPacket);
				        ObjectInputStream oin = new ObjectInputStream(bin);
				        
				        FTPPacket packetObject = null;
				        
				        try {
				        	if((packetObject = (FTPPacket) oin.readObject())==null){
				        		System.out.println("File transfer complete.. Saving File");
				        		saveFile();
				        		continue;
				        	}
						} catch (Exception e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
				        			        
						// Compute checksum and verify data
						if(generateChecksum(serialize(packetObject)) != 0){
							System.out.println("Incorrect checksum, Servers checksum = " + generateChecksum(udpPacket) + ", Client's checksum = " + packetObject.checksum);
							packetLoss = true;
						}
						
						// Generate random number and emulate packet loss
						Random randNum = new Random();
						if(randNum.nextDouble() < this.probability){
							System.out.println("Probability Loss");
							packetLoss = true;
						}
						
						//Check if packet is not out of order and set packet loss to true
						if(expectedSequenceNumber < packetObject.sequenceNumber){
							System.out.println("Out of order Packets");
							packetLoss = true;
						}
						
						// Send acknowledgement in case there is no packet loss
						if(!packetLoss){
							bufferFTPPackets.add(packetObject);
							short checksumField = 0;
							short ackIndicator = (short) 43690; // Decimal for "1010101010101010";
							if(expectedSequenceNumber < (packetObject.sequenceNumber + 1))
								expectedSequenceNumber = packetObject.sequenceNumber + 1;
							
							System.out.println("Sending acknowledgment for " + packetObject.sequenceNumber);
							FTPPacket ackPacket = new FTPPacket(packetObject.sequenceNumber, checksumField, ackIndicator);
					        ByteArrayOutputStream bout = new ByteArrayOutputStream();
					        ObjectOutputStream out = new ObjectOutputStream(bout);
					        out.writeObject(ackPacket);
					        
					        byte[] ackData 	= bout.toByteArray();
					        int length = ackData.length;
					        DatagramPacket dpAck = new DatagramPacket(ackData, length, packet.getAddress(), packet.getPort());
					        socket.send(dpAck);
						}else{
							System.out.println("Packet loss, sequence number = " + packetObject.sequenceNumber);
						}
						
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

				}
			}
		}
		
		public static byte[] serialize(Object obj) throws IOException {
	        try(ByteArrayOutputStream b = new ByteArrayOutputStream()){
	            try(ObjectOutputStream o = new ObjectOutputStream(b)){
	                o.writeObject(obj);
	            }
	            return b.toByteArray();
	        }
	    }

		public void saveFile() throws IOException{
			
			System.out.println("Writing data to file");
			
			File file=new File(filename);
			FileOutputStream fos = new FileOutputStream(file);
			for(FTPPacket pkt : bufferFTPPackets){
				fos.write(pkt.data);
			}
			System.out.println("File Saved");
			bufferFTPPackets.clear();
			
			expectedSequenceNumber = 0;
			
			fos.close();
		}

		public short generateChecksum(byte[] packet) {
			int checksum = 0;
			for (int i = 0; i < packet.length; i += 2) {
				int leftByte = (packet[i] << 8) & 0xFF00;
				int rightByte = (i + 1) < packet.length ? (packet[i + 1] & 0x00FF) : 0;
				checksum += (leftByte + rightByte);
				String hex = Integer.toHexString(checksum);

				if (hex.length() > 4) {
					int carry = Integer.parseInt(String.valueOf(hex.charAt(0)), 16);
					checksum = Integer.parseInt(hex.substring(1, 5), 16) + carry;
				}
			}
			// Complement the checksum value
			return (short)(Integer.parseInt("FFFF", 16) - checksum);
		}

	}

}
