import static java.net.InetAddress.getByName;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.concurrent.locks.Lock;

public class Client {
	private String server;
	private int port;
	private String filename;
	private int windowsSize;
	private int mss;
	private volatile int numberOfSentPackets = 0;
	private volatile Segment[] buffer;
	private DatagramSocket clientSocket;
	private volatile int dataAck;
	volatile long totalPackets;
	int sizeOfLastPacket;

	final int RTTTimer = 2000; // milliseconds

	public Client(String server, int port, String filename, int windowsSize, int mss) {
		this.server = server;
		this.port = port;
		this.filename = filename;
		this.windowsSize = windowsSize;
		this.mss = mss;
		this.totalPackets = -1;
		this.sizeOfLastPacket = -1;

		// Go-back-N: need buffer
		buffer = new Segment[windowsSize];

		dataAck = -1;

		try {
			clientSocket = new DatagramSocket(0);
		} catch (SocketException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			clientSocket.connect(getByName(server), port);
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {

		/*if (args == null || args.length != 5) {
			System.out.println("Invalid input");
			return;
		}*/

		String server_host = "localhost";//args[0];
		int port = 7735;//Integer.parseInt(args[1]); // 7735;
		String fileName = "test.pdf";//args[2];
		int windowSize = 1;//Integer.parseInt(args[3]);// 64;
		int mss = 500;//Integer.parseInt(args[4]);// 200; // bytes

		// for (mss = 100; windowSize <= 1000; windowSize += 100) {
		// for (int i = 0; i < 5; ++i) {
		// System.out.println("MSS = " + windowSize);
		long startTime = System.currentTimeMillis();

		new Client(server_host, port, fileName, windowSize, mss).run();

		File file = new File(fileName);

		long fileSize = file.length();

		long endTime = System.currentTimeMillis();

		/*
		 * System.out.println((i + 1) + ". MSS = " + mss + "\tDelay = " +
		 * (endTime - startTime) + "\tBytes Transferred = " + fileSize);
		 */

		System.out
				.println("Probability = 0.01\tDelay = " + (endTime - startTime) + "\tBytes Transferred = " + fileSize);
		// }
		// }

		// new Client("localhost", 7735, "test.pdf", 1, 500).run();
	}

	public void run() {
		File file = new File(filename);
		byte data[] = new byte[mss];
		try {
			if (file.exists()) {
				long fileSize = file.length();

				totalPackets = (long) Math.ceil((double) fileSize / this.mss);
				sizeOfLastPacket = (int) fileSize % this.mss;

				/*
				 * System.out.println("Total packets = " + totalPackets +
				 * ", size of last packet = " + sizeOfLastPacket);
				 */

				FileInputStream fis = new FileInputStream(file);

				// Starting the acknowledgment listener
				AcknowledgmentServer ackServer = new AcknowledgmentServer(this.clientSocket);
				ackServer.start();
				while (numberOfSentPackets < totalPackets) {
					
					int index = numberOfSentPackets % windowsSize;

					/*if (buffer[index] == null
							|| (buffer[index] != null && buffer[index].packet.sequenceNumber <= dataAck)) {
						// send the packet
						if (fis.read(data) > -1)
							rdtSend(data, index);
					}*/
					
					if (buffer[index] == null
							|| (buffer[index] != null && buffer[index].ackReceived == true)) {
						// send the packet
						if (fis.read(data) > -1)
							rdtSend(data, index);
					}

					/*
					 * if ((numberOfSentPackets <= dataAck) || (dataAck == -1 &&
					 * numberOfSentPackets < windowsSize)) { if(fis.read(data) >
					 * -1) rdtSend(data, index); }
					 */
				}

				// Send another packet with FTPPacket as null
				sendTermintingPacket();

				fis.close();
			}
		} catch (IOException e) {
			System.out.println(e);
		}
	}

	private void sendTermintingPacket() throws IOException {
		while (dataAck != totalPackets - 1) {
			// System.out.println("[TERMINATION]: dataAck = " + dataAck +
			// ", numberOfSentPackets" + numberOfSentPackets);
		}

		// System.out.println("Sending terminating packet");
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		ObjectOutputStream outStream = new ObjectOutputStream(bos);
		outStream.writeObject(null);
		byte[] sendData = bos.toByteArray();
		DatagramPacket dataPacket = new DatagramPacket(sendData, sendData.length, getByName(server), port);
		clientSocket.send(dataPacket);
	}

	private void rdtSend(byte[] data, int index) throws IOException {
		// TODO Auto-generated method stub
		boolean isLast = false;
		FTPPacket packet = null;

		if (numberOfSentPackets == totalPackets - 1) {
			// This is the last packet
			byte[] lastPacket = new byte[sizeOfLastPacket];

			for (int i = 0; i < lastPacket.length; ++i) {
				lastPacket[i] = data[i];
			}

			packet = new FTPPacket(numberOfSentPackets, (short) 0, (short) 21845, lastPacket);

			isLast = true;
		}

		// Packet to be sent
		if (!isLast) {
			packet = new FTPPacket(numberOfSentPackets, (short) 0, (short) 21845, data);
		}

		short checksum = generateChecksum(serialize(packet));

		// System.out.println("Checksum = " + checksum);

		packet.checksum = checksum;

		// Go-Back protocol: store packet in buffer
		buffer[numberOfSentPackets % windowsSize] = new Segment(packet);

		// Send Packet to server
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		ObjectOutputStream outStream = new ObjectOutputStream(bos);
		outStream.writeObject(packet);
		byte[] sendData = bos.toByteArray();
		DatagramPacket dataPacket = new DatagramPacket(sendData, sendData.length, getByName(server), port);
		clientSocket.send(dataPacket);

		// System.out.println("Kuch bhi = " +
		// generateChecksum(serialize(packet)));

		numberOfSentPackets++;

		buffer[index].setSentTime();
		new RetransmitHandler(index).start();

		// buffer[index].timer.schedule(new RetransmitHandler(index,
		// this.clientSocket), RTTTimer);
	}

	public static byte[] serialize(Object obj) throws IOException {
		try (ByteArrayOutputStream b = new ByteArrayOutputStream()) {
			try (ObjectOutputStream o = new ObjectOutputStream(b)) {
				o.writeObject(obj);
			}
			return b.toByteArray();
		}
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
				checksum = (Integer.parseInt(hex.substring(1, 5), 16) + carry);
			}
		}
		// Complement the checksum value
		return (short) (Integer.parseInt("FFFF", 16) - checksum);
	}

	private class AcknowledgmentServer extends Thread {
		private DatagramSocket socket;

		public AcknowledgmentServer(DatagramSocket socket) {
			this.socket = socket;
		}

		public void run() {
			byte databuffer[] = new byte[1024];
			int length = databuffer.length;
			DatagramPacket datagrampacket = new DatagramPacket(databuffer, length);
			if (!socket.isClosed()) {
				try {
					boolean send = true;
					while (send) {
						synchronized(datagrampacket){
						socket.receive(datagrampacket);
						ObjectInputStream outputStream = new ObjectInputStream(
								new ByteArrayInputStream(datagrampacket.getData()));
						FTPPacket packet = (FTPPacket) outputStream.readObject();
						if (packet.ackFlag == (short) 43690) {
							//dataAck = packet.sequenceNumber;
							
							if(dataAck < packet.sequenceNumber){
								dataAck = packet.sequenceNumber;
							}
							if(dataAck>packet.sequenceNumber){
							//	buffer[packet.sequenceNumber % windowsSize].sentTimestamp=System.currentTimeMillis();
							System.out.println("hiiiiii");
							}
							if(packet.sequenceNumber== buffer[packet.sequenceNumber % windowsSize].packet.sequenceNumber)
								buffer[packet.sequenceNumber % windowsSize].ackReceived = true;
						}

						if (dataAck == (totalPackets - 1)) {
							send = false;
						}
						}
					}
				} catch (Exception e) {
					System.out.println("Error occured..." + e.getMessage());
				}
			}
		}
	}

	private class RetransmitHandler extends Thread {
		int packetIndex;

		public RetransmitHandler(int packetIndex) {
			this.packetIndex = packetIndex;
		}

		public void run() {

			boolean cancelTimer = false;

			while (!cancelTimer) {
				
				long sentTimestamp = buffer[packetIndex].sentTimestamp;
				
				while ((System.currentTimeMillis() - sentTimestamp) < RTTTimer) {

				}

				if (buffer[packetIndex].packet != null && buffer[packetIndex].ackReceived == true) {
					/*
					 * System.out.println("Timer cancelled for seq. no. " +
					 * buffer[packetIndex].packet.sequenceNumber);
					 */
					cancelTimer = true;
				} else {
					/*
					 * System.out.println("Timeout, sequence number = " +
					 * buffer[packetIndex].packet.sequenceNumber +
					 * ", dataAck = " + dataAck);
					 */
					// retransmit the packet
					FTPPacket packet = new FTPPacket(buffer[packetIndex].packet.sequenceNumber, (short) 0,
							(short) 21845, buffer[packetIndex].packet.data);

					short checksum = 0;
					try {
						checksum = generateChecksum(serialize(packet));
					} catch (IOException e3) {
						// TODO Auto-generated catch block
						e3.printStackTrace();
					}

					packet.checksum = checksum;

					buffer[packetIndex] = new Segment(packet);

					// Send Packet to server
					ByteArrayOutputStream bos = new ByteArrayOutputStream();
					ObjectOutputStream outStream = null;
					try {
						outStream = new ObjectOutputStream(bos);
					} catch (IOException e2) {
						// TODO Auto-generated catch block
						e2.printStackTrace();
					}
					try {
						outStream.writeObject(packet);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

					byte[] sendData = bos.toByteArray();
					DatagramPacket dataPacket = null;

					try {
						dataPacket = new DatagramPacket(sendData, sendData.length, getByName(server), port);
					} catch (UnknownHostException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}

					try {
						clientSocket.send(dataPacket);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

					// Initiate the timer again
					buffer[packetIndex].setSentTime();
				}

			}

		}
	}
}