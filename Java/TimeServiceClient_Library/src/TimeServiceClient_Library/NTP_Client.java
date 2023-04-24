/*	File			NTP_Client.java
	Purpose			Network Time Protocol (NTP) Client - Library
	Author			Richard Anthony	(ar26@gre.ac.uk)
	Date			February 2015
*/
package TimeServiceClient_Library;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URL;

public class NTP_Client
{
    
    private static int NTP_Port = 123;
    private static int NTP_PACKET_SIZE = 48;// NTP time stamp is in the first 48 bytes of the message
    private static long SeventyYears = 2208988800L;  // Number of seconds in 70 years.
                                                // The raw timestamp is the number of seconds Since 1900
                                                // Unix time starts on Jan 1 1970 (70 years = 2208988800 seconds)
    DatagramSocket m_TimeService_Socket;
    InetAddress m_TimeService_IPAddress;
    Boolean m_bNTP_Client_Started;

    public enum NTP_Client_ResultCode {NTP_Success, NTP_ServerAddressNotSet, NTP_SendFailed, NTP_ReceiveFailed};

    public class NTP_Data
    {		
        public NTP_Client_ResultCode eResultCode;
        public int stratum;
        public int poll;
        public int precision;
        public int rootDelay;
        public int rootDispersion;
        public long refTimestamp;
        public long originTimestamp;
        public long recvTimestamp;
        public long transmitTimestamp;
        public long roundTripDelay;
        public long localClockOffset;
        public long lUnixTime;	// Seconds since 1970 (secsSince1900 - seventyYears)
        public long lHour;
        public long lMinute;
        public long lSecond;
        public long lMilisecond;
        NTP_Data()
        {
            eResultCode = NTP_Client_ResultCode.NTP_ServerAddressNotSet;
            lHour = 0;
            lMinute = 0;
            lSecond = 0;
            lMilisecond = 0;
            lUnixTime = 0;
            refTimestamp = 0;
            originTimestamp = 0;
            transmitTimestamp = 0;
            recvTimestamp = 0;
            roundTripDelay = 0;
            localClockOffset = 0;
        }
    };

    private Boolean m_bTimeServiceAddressSet;

    public NTP_Client()
    {	
        m_bTimeServiceAddressSet = false;
        m_bNTP_Client_Started = false;
    }

    public Boolean CreateSocket()
    {
        try
        {
            m_TimeService_Socket = new DatagramSocket();
            m_TimeService_Socket.setSoTimeout(500); // Timeout = 500ms (i.e. non blocking IO behaviour)
            // The timeout period was chosen so as to prevent the application freezing if the time service does not respond
	    // but that it waits long enough for the reply RTT , so that it mostly avoids missing an actual reply 
            // Tested with the following values: 100ms(can work but unreliable) 
            // 200ms(generally ok but highly dependent on network RTT) 400ms(generally reliable) 500ms(adds margin of safety)
        }
        catch (SocketException Ex)
        {
            return false;	
        }
        return true;
    }

    public InetAddress SetUp_TimeService_AddressStruct(String sURL)
    {         
        String sFullURL = "http://" +sURL;
        try
        {
            m_TimeService_IPAddress = InetAddress.getByName(new URL(sFullURL).getHost());
            m_bTimeServiceAddressSet = true;
        }
        catch (Exception Ex)
        {
            return null;
        }
        return m_TimeService_IPAddress;
    }
    
    public int GetPort()
    {
        return NTP_Port;
    }
        
    public NTP_Data Get_NTP_Timestamp()
    {
        NTP_Data ntpData = new NTP_Data();
        if(true == m_bTimeServiceAddressSet)
        {
            if(true == Send_TimeService_Request())
            {   // Send operation succeeded 
                ntpData = Receive(ntpData);
                if(0 != ntpData.lUnixTime)
                {
                    ntpData.eResultCode = NTP_Client_ResultCode.NTP_Success; // Signal that the NTP_Timestamp has been updated with valid content
                    return ntpData;
                }
                ntpData.eResultCode = NTP_Client_ResultCode.NTP_ReceiveFailed; // Signal that the receive operation failed (Time server did not reply)
                return ntpData;
            }
            ntpData.eResultCode = NTP_Client_ResultCode.NTP_SendFailed; // Signal that the send operation failed (Time server was not contacted)
            return ntpData;
        }
        ntpData.eResultCode = NTP_Client_ResultCode.NTP_ServerAddressNotSet; // Signal that Time server address has not been set, cannot get NTP timestamp
        return ntpData;
    }

    Boolean Send_TimeService_Request()
    {
        byte[] bSendBuf = new byte [NTP_PACKET_SIZE]; // Zero-out entire 48-byte buffer to hold incoming packets (UTC time value)
        // Initialize values needed to form NTP request
        bSendBuf[0] = (byte) 0xE3;// 0b11100011;   
        // LI bits 7,6		= 3 (Clock not synchronised), 
        // Version bits 5,4,3	= 4 (The current version of NTP)
        // Mode bits 2,1,0	= 3 (Sent by client)
        long currentMiliSecs = System.currentTimeMillis();
        Resolve_Miliseconds_NTP_Timestamp(bSendBuf, 40, currentMiliSecs);
        try
        {
            DatagramPacket SendPacket = new DatagramPacket(bSendBuf, bSendBuf.length,
                            m_TimeService_IPAddress /*The address to send to*/, NTP_Port); 
            m_TimeService_Socket.send(SendPacket);
        }
        catch(SocketTimeoutException Ex)
        {
             return false;
        }
        catch(Exception Ex)
        {
            System.out.printf("Send failed: %s\n", Ex.toString());
            return false;
        }
        return true;
    }

    private NTP_Data Receive(NTP_Data ntpData)
    {
        byte[] bRecvBuf = new byte [NTP_PACKET_SIZE]; // buffer to hold incoming packets (UTC time value)
        DatagramPacket RecvPacket = new DatagramPacket(bRecvBuf, NTP_PACKET_SIZE);
        try
        {
            m_TimeService_Socket.receive(RecvPacket);
        }
        catch(Exception ex)
        {
            ntpData.lUnixTime = 0; // Signal that an error occurred
            return ntpData;
        }

        if (0 < RecvPacket.getLength())
        {   // The timestamp starts at byte 40 of the received packet and is four bytes,
            ntpData.stratum = Resolve_NTP(bRecvBuf, 1, 1);
            ntpData.poll = Resolve_NTP(bRecvBuf, 2, 1);
            ntpData.precision = Resolve_NTP(bRecvBuf, 3, 1) - 256;
            ntpData.rootDelay = Resolve_NTP(bRecvBuf, 4, 4);
            ntpData.rootDispersion = Resolve_NTP(bRecvBuf, 8, 4);
            ntpData.refTimestamp = Resolve_NTP_Timestamp_Miliseconds(bRecvBuf, 16);
            ntpData.originTimestamp = Resolve_NTP_Timestamp_Miliseconds(bRecvBuf, 24);
            ntpData.recvTimestamp = Resolve_NTP_Timestamp_Miliseconds(bRecvBuf, 32);
            ntpData.transmitTimestamp = Resolve_NTP_Timestamp_Miliseconds(bRecvBuf, 40);
            long currentRecvTimestamp = System.currentTimeMillis();
            ntpData.roundTripDelay = (currentRecvTimestamp - ntpData.originTimestamp) - (ntpData.transmitTimestamp - ntpData.recvTimestamp);
            ntpData.localClockOffset = ((ntpData.recvTimestamp - ntpData.originTimestamp) + (ntpData.transmitTimestamp - System.currentTimeMillis())) / 2;
            ntpData.lUnixTime = System.currentTimeMillis() + ntpData.localClockOffset;
            ntpData.lHour = (long) (ntpData.lUnixTime / 1000L  % 86400L) / 3600;
            ntpData.lMinute = (long) (ntpData.lUnixTime / 1000L % 3600) / 60;
            ntpData.lSecond = (long) ntpData.lUnixTime / 1000L % 60;
            ntpData.lMilisecond = (long) ntpData.lUnixTime % 1000L;
        }
        else
        {
            ntpData.lUnixTime = 0; // Signal that an error occurred
        }
        return ntpData;
    }

    public Boolean Get_ClientStarted_Flag()
    {
        return m_bNTP_Client_Started;
    }

    public void Set_ClientStarted_Flag(Boolean bClient_Started)
    {
        m_bNTP_Client_Started = bClient_Started;
    }

    public void CloseSocket()
    {
        try
        {
            m_TimeService_Socket.close();
        }
        catch (Exception Ex)
        {   // Generic approach to dealing with situations such as socket not created
        }
    }

    private static int Resolve_NTP(byte[] buffer, int startOffset, int byteCount){
        int num = 0;
        for(int i = 0; i < byteCount; i++){
            num += ((int) buffer[startOffset+i] & 0xFF) << (byteCount-1-i)*8;
        }
        return num;
    }

    private static long Resolve_NTP_Timestamp_Miliseconds(byte[] buffer, int startOffset){
        long intPart = 0;
        for(int i = 0; i < 4; i++){
            intPart += ((long) buffer[startOffset+i] & 0xFF) << (3-i)*8;
        }
        long fracPart = 0;
        for(int i = 0; i < 4; i++){
            fracPart += ((long) buffer[startOffset+i+4] & 0xFF) << (3-i)*8;
        }
        long secondsSince1970 = intPart - SeventyYears;
        long milisecondsSince1970 = secondsSince1970 * 1000;
        long fracMiliseconds = (fracPart * 1000 + (1L << 31)) >>> 32;
        return milisecondsSince1970 + fracMiliseconds;
    }

    private static void Resolve_Miliseconds_NTP_Timestamp(byte[] buffer, int startOffset, long milliseconds) {
        long secondsSince1900 = milliseconds / 1000 + SeventyYears;
        long fractionalMilliseconds = ((milliseconds % 1000) * (1L << 32)) / 1000;
        long ntpTimestamp = (secondsSince1900 << 32) | fractionalMilliseconds;
        for (int i = startOffset; i <startOffset + 8; i++) {
            buffer[i] = (byte) (ntpTimestamp >>> (8 * (7 - i)));
        }
    }
}