/*	Application		Time Service (NTP) Client		
	File			TimeServiceClient_CONSOLE_uses_library.java
	Purpose			NTP client - using a custom library
	Author			Richard John Anthony
	Date			March 2015
	
    To run the project from the command line, go to the dist folder and type: java -jar TimeServiceClient_CONSOLE_uses_library.jar
*/
package TimeServiceClient_CONSOLE_uses_library;

import TimeServiceClient_Library.NTP_Client;    // The import for the libray (TimeServiceClient_Library)
                                                // Specifically, the NTP_Client class within the library
import java.net.*;

public class TimeServiceClient_CONSOLE_uses_library 
{
    NTP_Client m_NTP_Client; // Can be written longhand as TimeServiceClient_Library.NTP_Client which emphasises the usage of the library 

    private static  int  m_iNumRequestsSent;
    private static  int  m_iNumResponsesReceived;

    public static void main(String[] args)
    {
        TimeServiceClient_CONSOLE_uses_library TimeServClnt = new TimeServiceClient_CONSOLE_uses_library();

        System.out.println("\nTime Service Client (Console Application - uses library)");
        System.out.println("--------------------------------------------------------");

        String sDomain = TimeServClnt.Parse_CommandLineArguments(args);
            System.out.printf("Time Service Domain: %s\n", sDomain);
        TimeServClnt.SetUp_TimeService_AddressStruct(sDomain);
            System.out.println("Ask Time Service for timestamp 5 times, at 5-second intervals:\n");
        TimeServClnt.m_NTP_Client.Set_ClientStarted_Flag(true);

        for (int iLoop = 0; iLoop < 5; iLoop++)
        {
            TimeServClnt.Get_NTP_Timestamp();
            try
            {
                Thread.sleep(5000); // 5 second delay between loop iterations
            }
            catch (InterruptedException Ex)
            {
            }
        }
        TimeServClnt.Quit(0);
    }
    
    TimeServiceClient_CONSOLE_uses_library()
    {
        m_NTP_Client = new NTP_Client();
        Boolean bSocketOpenSuccess = m_NTP_Client.CreateSocket();
        if (false == bSocketOpenSuccess)
        {   // Library not initialised - could not open socket, or could not set socket to non-blocking mode
            System.out.println("!!! Failed to initialise Time Service Client library !!!   QUITTING");
            Quit(-1);
        }
    }

    String Parse_CommandLineArguments(String[] args)
    {
        String sProgramName = new java.io.File(TimeServiceClient_CONSOLE_uses_library.class.getProtectionDomain()
                                                .getCodeSource().getLocation().getPath()).getName();
        try
        {
            if (1 != args.length)
            {   // User did not provide the required parameters
                DisplayUsage(sProgramName);
                System.out.println("!!! Command Line Arguments invalid !!!   QUITTING");
                Quit(-2);
            }

            int iOption = Integer.parseInt(args[0]);  // Resolves to 0 if characters present, therefore options start at 1
            if(1 > iOption || 12 < iOption)
            {   // Option value was out of range
                DisplayUsage(sProgramName);
                System.out.println("!!! Command Line Arguments invalid !!!   QUITTING");
                Quit(-3);
            }

            switch(iOption) {
                case 1: 
                        return "time.nist.gov";
                case 2: 
                        return "nist1-ny.ustiming.org";
                case 3: 
                        return "nist1-nj.ustiming.org";
                case 4: 
                        return "nist1-atl.ustiming.org";
                case 5: 
                        return "nist1-chi.ustiming.org";
                case 6: 
                        return "nist1-lnk.binary.net";
                case 7: 
                        return "time-a.timefreq.bldrdoc.gov";
                case 8: 
                        return "ntp-nist.ldsbc.edu";
                case 9: 
                        return "nist1-lv.ustiming.org";
                case 10: 
                        return "nist1-la.ustiming.org";
                case 11: 
                        return "time.windows.com";
                case 12: 
                        return "wolfnisttime.com";	
            }
            return "time.nist.gov";
        }
        catch (Exception Ex)
        {
            DisplayUsage(sProgramName);
            System.out.println("!!! Command Line Arguments invalid !!!   QUITTING");
            Quit(-4);
        }
        return "";
    }

    void Get_NTP_Timestamp()
    {
        NTP_Client.NTP_Timestamp_Data NTP_Timestamp = m_NTP_Client.Get_NTP_Timestamp();
        switch (NTP_Timestamp.eResultCode)
        {
            case NTP_Success:
                m_iNumRequestsSent++;
                m_iNumResponsesReceived++;

                System.out.printf("  Unix Time (seconds sine 1970):%d\n", NTP_Timestamp.lUnixTime);
                System.out.printf("  Current time   Hour:%02d Minute:%02d Second:%02d\n", 
                                NTP_Timestamp.lHour, NTP_Timestamp.lMinute, NTP_Timestamp.lSecond);
                 // NTP does not indicate information specifcally related to local time zones such as BST or other daylight savings time offsets
                System.out.printf("  Number of NTP requests sent:%d  Number of NTP responses received:%d\n\n",
                                                        m_iNumRequestsSent, m_iNumResponsesReceived); 
                break;
            case NTP_ServerAddressNotSet:
                System.out.println("!!! Time Service Client Library not initialised with Time server Address !!!");
                break;
            case NTP_SendFailed:
                System.out.println("!!! NTP Send Failed in Time Service Client Library !!!");
                break;
            case NTP_ReceiveFailed:
                System.out.println("!!! NTP Receive Failed in Time Service Client Library !!!");
                m_iNumRequestsSent++;
                System.out.printf("  Number of NTP requests sent:%d  Number of NTP responses received:%d\n\n",
                                                        m_iNumRequestsSent, m_iNumResponsesReceived); 
                break;
        }
    }

    void SetUp_TimeService_AddressStruct(String sURL)
    {
        InetAddress TimeService_IPAddress = m_NTP_Client.SetUp_TimeService_AddressStruct(sURL);
        if(null != TimeService_IPAddress)
        {
            int iPort = m_NTP_Client.GetPort();
            System.out.printf("Time Service address (resolved by the library): %s:%d\n",
                                        TimeService_IPAddress.getHostAddress(), iPort);
        }
        else
        {
            System.out.printf("Time Service address !!not found!!");
        }
    }

    void DisplayUsage(String sProgramName)
    {
        System.out.printf("Usage:\n%s  Time-Service-Domain-Name-Option\n", sProgramName);	
        System.out.println("Select from the following Time Service Domain Names\n(enter the numerical index on the command line):");	
        System.out.println(" 1   time.nist.gov                (Location: NIST round robin load equalisation");
        System.out.println(" 2   nist1-ny.ustiming.org        (Location: New York City, NY");	
        System.out.println(" 3   nist1-nj.ustiming.org        (Location: Bridgewater, NJ");	
        System.out.println(" 4   nist1-atl.ustiming.org       (Location: Atlanta, Georgia");	
        System.out.println(" 5   nist1-chi.ustiming.org       (Location: Chicago, Illinois");	
        System.out.println(" 6   nist1-lnk.binary.net         (Location: Lincoln, Nebraska" );	
        System.out.println(" 7   time-a.timefreq.bldrdoc.gov  (Location: NIST, Boulder, Colorado");	
        System.out.println(" 8   ntp-nist.ldsbc.edu           (Location: LDSBC, Salt Lake City, Utah");	
        System.out.println(" 9   nist1-lv.ustiming.org        (Location: Las Vegas, Nevada");	  
        System.out.println("10   nist1-la.ustiming.org        (Location: Los Angeles, California");	
        System.out.println("11   time.windows.com             (Location: Windows Time service");	
        System.out.println("12   wolfnisttime.com             (Location: Birmingham, Alabama");	
    }

    void Quit(int iExitCode)
    {
        m_NTP_Client.CloseSocket();
        System.exit(iExitCode);
    }
}