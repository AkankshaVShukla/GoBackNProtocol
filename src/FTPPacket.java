import java.io.Serializable;

public class FTPPacket implements Serializable, Comparable<FTPPacket> {
	
	private static final long serialVersionUID = 1L;
	int sequenceNumber; 	// 32-bit sequence number
	short checksum; 		// 16-bit checksum
	short ackFlag;			// 16-bit acknowledgement flag
	
	byte[] data;
	
	public FTPPacket(int sequenceNumber, short checksum, short ackFlag){
		this.sequenceNumber = sequenceNumber;
		this.checksum 		= checksum;
		this.ackFlag 		= ackFlag;
	}
	
	public FTPPacket(int sequenceNumber, short checksum, short ackFlag, byte[] data){
		this.sequenceNumber = sequenceNumber;
		this.checksum 		= checksum;
		this.ackFlag 		= ackFlag;
		
		this.data = data;
	}

	@Override
	public int compareTo(FTPPacket o) {
		if(this.sequenceNumber < o.sequenceNumber)
			return -1;
		else if(this.sequenceNumber > o.sequenceNumber)
			return 1;
		
		return 0;
	}	
}
