
public class Segment {
	FTPPacket packet;
    long sentTimestamp;
    
    boolean ackReceived; // Added for Selective ARQ to track the acknowledgements
    
	public Segment(FTPPacket packet){
		this.packet=packet;
		this.ackReceived = false;
	}
	
	public void setSentTime(){
		sentTimestamp = System.currentTimeMillis();
	}
}


