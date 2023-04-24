/*	File			TimeServiceClient_GUI_uses_library.java
	Purpose			Network Time Protocol (NTP) Client
	Author			Richard Anthony	(ar26@gre.ac.uk)
	Date			March 2015
*/

package TimeServiceClient_GUI_uses_library;

import TimeServiceClient_Library.NTP_Client;    // The import for the libray (TimeServiceClient_Library)
                                                // Specifically, the NTP_Client class within the library
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.net.*;
import javax.swing.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.Timer;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
    
public class TimeServiceClient_GUI_uses_library extends javax.swing.JFrame implements ActionListener
{
    NTP_Client m_NTP_Client; // Can be written longhand as TimeServiceClient_Library.NTP_Client which emphasises the usage of the library 
    
    int m_iNumRequestsSent;
    int m_iNumResponsesReceived;
    Timer m_Timer_SendNTPRequests;
    Map<String, List<Long>> sourcesOffsetHistory;
    DefaultListModel m_listModel_NTPServerList; // For use with JList
    DefaultListModel m_listModel_LocationList; // For use with JList
    DefaultListModel m_listModel_StatusList;
    DefaultListModel m_listModel_OffsetList;
    DefaultListModel m_listModel_DelayList;
    DefaultListModel m_listModel_JitterList;
    int currentSelectedIndex = 0;
    int lastSelectedIndex = 0;
    ListSelectionListener m_SelectionListener_NTPServerURLs;

    public TimeServiceClient_GUI_uses_library() {
        m_SelectionListener_NTPServerURLs = new ListSelectionListener() // Listener for the 'URL selection changed' event
        {
            public void valueChanged(ListSelectionEvent listSelectionEvent) {
                int iSelectionIndex = jList_NTPServerURLs.getSelectedIndex();
                lastSelectedIndex = currentSelectedIndex;
                currentSelectedIndex = iSelectionIndex;
                jList_NTPServerLocations.setSelectedIndex(iSelectionIndex);
                jList_NTPServerStatus.setSelectedIndex(iSelectionIndex);
                jList_NTPServerOffset.setSelectedIndex(iSelectionIndex);
                jList_NTPServerDelay.setSelectedIndex(iSelectionIndex);
                jList_NTPServerJitter.setSelectedIndex(iSelectionIndex);

                Get_ServerURL_listBox_Selection();
                jTextField_Stratum.setText("");
                jTextField_Poll.setText("");
                jTextField_Precision.setText("");
                jTextField_UNIX_Time.setText("");
                jTextField_UTC_Time.setText("");
                m_iNumRequestsSent = 0;
                m_iNumResponsesReceived = 0;
                UpdateStatisticsDisplay();
            }
        };
        sourcesOffsetHistory = new HashMap<>();

        m_listModel_NTPServerList = new DefaultListModel(); // For use with jList_NTPServerURLs
        m_listModel_LocationList = new DefaultListModel(); // For use with jList_NTPServerLocations
        m_listModel_StatusList = new DefaultListModel();
        m_listModel_OffsetList = new DefaultListModel();
        m_listModel_DelayList = new DefaultListModel();
        m_listModel_JitterList = new DefaultListModel();
        initComponents();
        Populate_NTP_Server_List();

        m_NTP_Client = new NTP_Client();
        Boolean bSocketOpenSuccess = m_NTP_Client.CreateSocket();
        if (false == bSocketOpenSuccess) {
            JOptionPane.showMessageDialog(null, "Error creating socket", "NTP client", JOptionPane.PLAIN_MESSAGE);
            CloseSocketAndExit();
        }

        m_iNumRequestsSent = 0;
        m_iNumResponsesReceived = 0;
        UpdateStatisticsDisplay();
        InitialiseControls();
    }

    private void InitialiseControls()    
    {    
        jPanel_NTPServerAddressDetails.setEnabled(false);
        jTextField_Source.setEnabled(false);
        jTextField_Port.setEnabled(false);
        jTextField_ServerIPAddress.setEnabled(false);
        jTextField_Stratum.setEnabled(false);
        jTextField_Poll.setEnabled(false);
        jTextField_Precision.setEnabled(false);
        jTextField_UNIX_Time.setEnabled(false);
        jTextField_UTC_Time.setEnabled(false);
        jTextField_NumRequestsSent.setEnabled(false);
        jTextField_NumResponsesReceived.setEnabled(false);
        jList_NTPServerURLs.setEnabled(true);
        JScrollPane_NTPServerURLs.setEnabled(true);
        jList_NTPServerLocations.setEnabled(false);
        jList_NTPServerStatus.setEnabled(false);
        jList_NTPServerOffset.setEnabled(false);
        jList_NTPServerDelay.setEnabled(false);
        jList_NTPServerJitter.setEnabled(false);
        jScrollPane_NTPServerLocations.setEnabled(false);
        jScrollPane_NTPServerStatus.setEnabled(false);
        jScrollPane_NTPServerOffset.setEnabled(false);
        jScrollPane_NTPServerDelay.setEnabled(false);
        jScrollPane_NTPServerJitter.setEnabled(false);
        jTextField_Source.setEnabled(true);
        jTextField_Source.setToolTipText("Enter the source URL/IP");
        jTextField_Description.setEnabled(true);
        jTextField_Description.setToolTipText("Enter the source location/description");
        jButton_AddSource.setEnabled(true);
        jButton_StartNTPClient.setEnabled(true);
        jButton_Done.setEnabled(true); 
        Initialise_ServerURL_listBox(); // Selects first item in list boxes, by default
    }

    private void Record_Source_Clock_Offset_History(long offset){
        String currentServerName = jTextField_Source.getText();
        if(sourcesOffsetHistory.containsKey(currentServerName)){
            sourcesOffsetHistory.get(currentServerName).add(offset);
        } else {
            List<Long> newList = new ArrayList<>();
            newList.add(offset);
            sourcesOffsetHistory.put(currentServerName, newList);
        }
    }

    public static double Calculate_Jitter(List<Long> localClockOffsets) {
        if (localClockOffsets == null || localClockOffsets.size() < 2) {
            return 0d;
        }
        double sumTimeDifferences = 0.0;
        int timeDifferenceCount = 0;
        for (int i = 1; i < localClockOffsets.size(); i++) {
            double currentTimeDifference = Math.abs(localClockOffsets.get(i) - localClockOffsets.get(i - 1));
            sumTimeDifferences += currentTimeDifference;
            timeDifferenceCount++;
        }
        return sumTimeDifferences / timeDifferenceCount;
    }
        
    void Start_Timer_SendNTPRequests()
    {
        m_Timer_SendNTPRequests = new Timer();
        m_Timer_SendNTPRequests.scheduleAtFixedRate(
                new Get_NTP_Timestamp(), 100, 10000); // Initial timeout ocurs after 100 ms (sends first NTP request)
                                                      // Subsequent NTP request occur at 10-second intervals) 
    }

    class Get_NTP_Timestamp extends TimerTask
    {
        public void run()
        {
            NTP_Client.NTP_Data NTP_Timestamp = m_NTP_Client.Get_NTP_Timestamp();
            switch (NTP_Timestamp.eResultCode)
            {
                case NTP_Success:
                    m_iNumRequestsSent++;
                    m_iNumResponsesReceived++;
                    jTextField_Stratum.setText(Long.toString(NTP_Timestamp.stratum));
                    jTextField_Poll.setText(Long.toString(NTP_Timestamp.poll));
                    jTextField_Precision.setText(Long.toString(NTP_Timestamp.precision));
                    jTextField_UNIX_Time.setText(Long.toString(NTP_Timestamp.lUnixTime));
                    String sUTC_Time = String.format("%02d:%02d:%02d.%03d", NTP_Timestamp.lHour, NTP_Timestamp.lMinute, NTP_Timestamp.lSecond, NTP_Timestamp.lMilisecond);
                    jTextField_UTC_Time.setText(sUTC_Time);
                    Record_Source_Clock_Offset_History(NTP_Timestamp.localClockOffset);
                    List<Long> record = sourcesOffsetHistory.get(jTextField_Source.getText());
                    double jitter = 0;
                    UpdateStatisticsDisplay();
                    if(record != null){
                        jitter = Calculate_Jitter(record);
                    }
                    UpdateList(true, NTP_Timestamp.localClockOffset, NTP_Timestamp.roundTripDelay, jitter);
                    break;
                case NTP_ServerAddressNotSet:
                    break;
                case NTP_SendFailed:
                    UpdateList(false, 0, 0, 0);
                    break;
                case NTP_ReceiveFailed:
                    m_iNumRequestsSent++;
                    UpdateStatisticsDisplay();
                    UpdateList(false, 0, 0, 0);
                    break;
            }
        }
    }

    void SourceListAddElement(String source, String description){
        m_listModel_NTPServerList.addElement(source);
        m_listModel_LocationList.addElement(description);
        m_listModel_StatusList.addElement("Pending");
        m_listModel_OffsetList.addElement("--");
        m_listModel_DelayList.addElement("--");
        m_listModel_JitterList.addElement("--");
    }

    void Populate_NTP_Server_List()
    {
        try (BufferedReader br = Files.newBufferedReader(Paths.get("source.csv"))) {
            String DELIMITER = ",";
            String line;
            while ((line = br.readLine()) != null) {
                String[] columns = line.split(DELIMITER);
                if(columns.length == 2) {
                    SourceListAddElement(columns[0].trim(),columns[1].trim());
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
//        m_listModel_NTPServerList.addElement("time.nist.gov");
//        m_listModel_LocationList.addElement("NIST round robin load equalisation");
//        m_listModel_NTPServerList.addElement("time.windows.com");
//        m_listModel_LocationList.addElement("Windows Time service");
//        m_listModel_NTPServerList.addElement("nist1-atl.ustiming.org");
//        m_listModel_LocationList.addElement("Atlanta, Georgia");
//        m_listModel_NTPServerList.addElement("wolfnisttime.com");
//        m_listModel_LocationList.addElement("Birmingham, Alabama");
//        m_listModel_NTPServerList.addElement("nist1-chi.ustiming.org");
//        m_listModel_LocationList.addElement("Chicago, Illinois");
//        m_listModel_NTPServerList.addElement("nist1-lnk.binary.net");
//        m_listModel_LocationList.addElement("Lincoln, Nebraska");
//        m_listModel_NTPServerList.addElement("time-a.timefreq.bldrdoc.gov");
//        m_listModel_LocationList.addElement("NIST, Boulder, Colorado");
//        m_listModel_NTPServerList.addElement("ntp-nist.ldsbc.edu");
//        m_listModel_LocationList.addElement("LDSBC, Salt Lake City, Utah");
//        m_listModel_NTPServerList.addElement("nist1-lv.ustiming.org");
//        m_listModel_LocationList.addElement("Las Vegas, Nevada");
//        m_listModel_NTPServerList.addElement("nist1-la.ustiming.org");
//        m_listModel_LocationList.addElement("Los Angeles, California");
//        m_listModel_NTPServerList.addElement("nist1-ny.ustiming.org");
//        m_listModel_LocationList.addElement("New York City, NY");
//        m_listModel_NTPServerList.addElement("nist1-nj.ustiming.org");
//        m_listModel_LocationList.addElement("Bridgewater, NJ");
    }

    private void Initialise_ServerURL_listBox()
    {
        jList_NTPServerURLs.setSelectedIndex(0);
        jList_NTPServerLocations.setSelectedIndex(0);
        jList_NTPServerStatus.setSelectedIndex(0);
        jList_NTPServerOffset.setSelectedIndex(0);
        jList_NTPServerDelay.setSelectedIndex(0);
        jList_NTPServerJitter.setSelectedIndex(0);
        Get_ServerURL_listBox_Selection();
        currentSelectedIndex = 0;
        lastSelectedIndex = 0;
    }

    private void Get_ServerURL_listBox_Selection()
    {
        String sSelectedURL = jList_NTPServerURLs.getSelectedValue().toString();
        jTextField_Source.setText(sSelectedURL);
        SetUp_TimeService_AddressStruct(sSelectedURL);
    }

    void SetUp_TimeService_AddressStruct(String sURL)
    {
        InetAddress TimeService_IPAddress = m_NTP_Client.SetUp_TimeService_AddressStruct(sURL);
        if(null != TimeService_IPAddress)
        {
            m_listModel_StatusList.set(currentSelectedIndex, "Ready");
            jTextField_ServerIPAddress.setText(TimeService_IPAddress.getHostAddress());
            jTextField_Port.setText(Integer.toString(m_NTP_Client.GetPort()));
        }
        else
        {
            m_listModel_StatusList.set(currentSelectedIndex, "Not Found");
            jTextField_ServerIPAddress.setText("Not found");
            jTextField_Port.setText("");
        }
    }

    void UpdateStatisticsDisplay()
    {
        jTextField_NumRequestsSent.setText(Integer.toString(m_iNumRequestsSent));
        jTextField_NumResponsesReceived.setText(Integer.toString(m_iNumResponsesReceived));
    }

    void UpdateList(boolean success, long offset, long delay, double jitter){
        if(success){
            m_listModel_StatusList.set(currentSelectedIndex, "Success");
            m_listModel_OffsetList.set(currentSelectedIndex, offset+"ms");
            m_listModel_DelayList.set(currentSelectedIndex, delay+"ms");
            m_listModel_JitterList.set(currentSelectedIndex, String.format("%.02f",jitter));
        } else {
            m_listModel_StatusList.set(currentSelectedIndex, "Fail");
        }
    }

    void StopTimer()
    {
        if (null != m_Timer_SendNTPRequests)
        {
            m_Timer_SendNTPRequests.cancel();
        }
    }

    void CloseSocketAndExit()
    {
        StopTimer();
        m_NTP_Client.CloseSocket();
        System.exit(0);
    }
    
    public static void main(String args[]) throws Exception
    {   // Initialise the GUI libraries
        try 
        {
            javax.swing.UIManager.LookAndFeelInfo[] installedLookAndFeels=javax.swing.UIManager.getInstalledLookAndFeels();
            for (int idx=0; idx<installedLookAndFeels.length; idx++)
            {
                if ("Nimbus".equals(installedLookAndFeels[idx].getName())) {
                    javax.swing.UIManager.setLookAndFeel(installedLookAndFeels[idx].getClassName());
                    break;
                }
            }
        } 
        catch (Exception Ex)
        {
            System.exit(0);
        }

        java.awt.EventQueue.invokeAndWait(new Runnable() // Create and display the GUI form
        { 
            public void run() 
            { 
                new TimeServiceClient_GUI_uses_library().setVisible(true);
            }
        } );
    }    

    public void actionPerformed(ActionEvent e)
    {
        if(jButton_Done == e.getSource())
        {
            CloseSocketAndExit();
        }
        if(jButton_StartNTPClient == e.getSource())
        {
            if(false == m_NTP_Client.Get_ClientStarted_Flag())
            {
                jButton_StartNTPClient.setText("STOP NTP requests");
                jList_NTPServerURLs.setEnabled(false);
                m_iNumRequestsSent = 0;
                m_iNumResponsesReceived = 0;
                UpdateStatisticsDisplay();
                Start_Timer_SendNTPRequests();
                m_NTP_Client.Set_ClientStarted_Flag(true);
            }
            else
            {
                jButton_StartNTPClient.setText("START NTP requests");
                jList_NTPServerURLs.setEnabled(true);
                m_NTP_Client.Set_ClientStarted_Flag(false);
                StopTimer();
            }
        }
        if(jButton_AddSource == e.getSource()){
            SourceListAddElement(jTextField_URL_OR_IP.getText(), jTextField_Description.getText());
        }
    }
       
    private JPanel jPanel_NTPServerAddressDetails;
    private JLabel jLabel_URL;
    private JTextField jTextField_Source;
    private JLabel jLabel_Port;
    private JTextField jTextField_Port;
    private JLabel jLabel_ServerIPAddress;
    private JTextField jTextField_ServerIPAddress;
    private JPanel jPanel_Time_Status;
    private JLabel jLabel_Stratum;
    private JTextField jTextField_Stratum;
    private JLabel jLabel_Poll;
    private JTextField jTextField_Poll;
    private JLabel jLabel_Precision;
    private JTextField jTextField_Precision;
    private JLabel jLabel_UNIX_Time;
    private JTextField jTextField_UNIX_Time;
    private JLabel jLabel_UTC_Time;
    private JTextField jTextField_UTC_Time;
    private JLabel jLabel_NumRequestsSent;
    private JTextField jTextField_NumRequestsSent;
    private JLabel jLabel_NumResponsesReceived;
    private JTextField jTextField_NumResponsesReceived;
    private JPanel jPanel_NTPServerSelection;
    private JLabel jLabel_NIST_Servers;
    private JLabel jLabel_NTPServerURLs;
    private JLabel jLabel_NTPServerLocations;
    private JLabel jLabel_NTPServerStatus;
    private JLabel jLabel_NTPServerOffset;
    private JLabel jLabel_NTPServerDelay;
    private JLabel jLabel_NTPServerJitter;
    private JList jList_NTPServerURLs;
    private JScrollPane JScrollPane_NTPServerURLs;
    private JList jList_NTPServerLocations;
    private JScrollPane jScrollPane_NTPServerLocations;
    private JList jList_NTPServerStatus;
    private JScrollPane jScrollPane_NTPServerStatus;
    private JList jList_NTPServerOffset;
    private JScrollPane jScrollPane_NTPServerOffset;
    private JList jList_NTPServerDelay;
    private JScrollPane jScrollPane_NTPServerDelay;
    private JList jList_NTPServerJitter;
    private JScrollPane jScrollPane_NTPServerJitter;
    private JPanel jPanel_AddSource;
    private JTextField jTextField_URL_OR_IP;
    private JTextField jTextField_Description;
    private JButton jButton_AddSource;
    private JPanel jPanel_Controls;
    private JButton jButton_StartNTPClient;
    private JButton jButton_Done;

    private void initComponents()
    {
        jLabel_URL = new JLabel();
        jLabel_URL.setText("URL");
        jTextField_Source = new JTextField();
        jTextField_Source.setMaximumSize(new Dimension(250, 4));
        jTextField_Source.setHorizontalAlignment(JTextField.CENTER);
        jLabel_Port = new JLabel();
        jLabel_Port.setText("Port");
        jTextField_Port = new JTextField();
        jTextField_Port.setMaximumSize(new Dimension(120, 4));
        jTextField_Port.setHorizontalAlignment(JTextField.CENTER);
        jLabel_ServerIPAddress = new JLabel();
        jLabel_ServerIPAddress.setText("Server IP address");
        jTextField_ServerIPAddress = new JTextField();
        jTextField_ServerIPAddress.setMaximumSize(new Dimension(120, 4));
        jTextField_ServerIPAddress.setHorizontalAlignment(JTextField.CENTER);

        jPanel_NTPServerAddressDetails = new JPanel();
        jPanel_NTPServerAddressDetails.setPreferredSize(new Dimension(500, 400));
        jPanel_NTPServerAddressDetails.setBorder(BorderFactory.createTitledBorder("Selected NTP Server Address"));
        org.jdesktop.layout.GroupLayout jPanel1Layout = new org.jdesktop.layout.GroupLayout(jPanel_NTPServerAddressDetails);
        jPanel_NTPServerAddressDetails.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.CENTER)
                .add(jPanel1Layout.createSequentialGroup()
                    .add(jLabel_URL)
                    .addContainerGap(10, 10)
                    .add(jTextField_Source))
                .add(jPanel1Layout.createSequentialGroup()
                    .add(jLabel_Port)
                    .addContainerGap(10, 10)
                    .add(jTextField_Port))
                .add(jPanel1Layout.createSequentialGroup()
                    .add(jLabel_ServerIPAddress)
                    .addContainerGap(10, 10)
                    .add(jTextField_ServerIPAddress)));
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.CENTER)
                .add(jPanel1Layout.createSequentialGroup()
                    .add(jPanel1Layout.createParallelGroup()
                        .add(jLabel_URL)
                        .add(jTextField_Source))
                    .addContainerGap(20, 20)
                    .add(jPanel1Layout.createParallelGroup()
                        .add(jLabel_Port)
                        .add(jTextField_Port))
                    .addContainerGap(20, 20)
                    .add(jPanel1Layout.createParallelGroup()
                        .add(jLabel_ServerIPAddress)
                        .add(jTextField_ServerIPAddress))));
        // stratum
        jLabel_Stratum = new JLabel();
        jLabel_Stratum.setText("Stratum");
        jTextField_Stratum = new JTextField();
        jTextField_Stratum.setMaximumSize(new Dimension(120, 4));
        jTextField_Stratum.setHorizontalAlignment(JTextField.CENTER);
        // poll
        jLabel_Poll = new JLabel();
        jLabel_Poll.setText("Poll");
        jTextField_Poll = new JTextField();
        jTextField_Poll.setMaximumSize(new Dimension(120, 4));
        jTextField_Poll.setHorizontalAlignment(JTextField.CENTER);
        // precision
        jLabel_Precision = new JLabel();
        jLabel_Precision.setText("Precision");
        jTextField_Precision = new JTextField();
        jTextField_Precision.setMaximumSize(new Dimension(120, 4));
        jTextField_Precision.setHorizontalAlignment(JTextField.CENTER);

        jLabel_UNIX_Time = new JLabel();
        jLabel_UNIX_Time.setText("UNIX time");
        jTextField_UNIX_Time = new JTextField();
        jTextField_UNIX_Time.setMaximumSize(new Dimension(120, 4));
        jTextField_UNIX_Time.setHorizontalAlignment(JTextField.CENTER);
        jLabel_UTC_Time = new JLabel();
        jLabel_UTC_Time.setText("UTC time");
        jTextField_UTC_Time = new JTextField();
        jTextField_UTC_Time.setMaximumSize(new Dimension(120, 4));
        jTextField_UTC_Time.setHorizontalAlignment(JTextField.CENTER);
        jLabel_NumRequestsSent = new JLabel();
        jLabel_NumRequestsSent.setText("Number of NTP time requests sent");
        jTextField_NumRequestsSent = new JTextField();
        jTextField_NumRequestsSent.setMaximumSize(new Dimension(60, 4));
        jTextField_NumRequestsSent.setHorizontalAlignment(JTextField.CENTER);
        jLabel_NumResponsesReceived = new JLabel();
        jLabel_NumResponsesReceived.setText("Number of NTP time responses received");
        jTextField_NumResponsesReceived = new JTextField();
        jTextField_NumResponsesReceived.setMaximumSize(new Dimension(60, 4));
        jTextField_NumResponsesReceived.setHorizontalAlignment(JTextField.CENTER);

        jPanel_Time_Status = new JPanel();
        jPanel_Time_Status.setPreferredSize(new Dimension(400, 300));
        jPanel_Time_Status.setBorder(BorderFactory.createTitledBorder("Time and Status"));
        org.jdesktop.layout.GroupLayout jPanel2Layout = new org.jdesktop.layout.GroupLayout(jPanel_Time_Status);
        jPanel_Time_Status.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.CENTER)
                .add(jPanel2Layout.createSequentialGroup()
                    .add(jLabel_Stratum)
                    .addContainerGap(10, 10)
                    .add(jTextField_Stratum))
                .add(jPanel2Layout.createSequentialGroup()
                    .add(jLabel_Poll)
                    .addContainerGap(10, 10)
                    .add(jTextField_Poll))
                .add(jPanel2Layout.createSequentialGroup()
                    .add(jLabel_Precision)
                    .addContainerGap(10, 10)
                    .add(jTextField_Precision))
                .add(jPanel2Layout.createSequentialGroup()
                    .add(jLabel_UNIX_Time)
                    .addContainerGap(10, 10)
                    .add(jTextField_UNIX_Time))
                .add(jPanel2Layout.createSequentialGroup()
                    .add(jLabel_UTC_Time)
                    .addContainerGap(10, 10)
                    .add(jTextField_UTC_Time))
                .add(jLabel_NumRequestsSent)
                .add(jTextField_NumRequestsSent)
                .add(jLabel_NumResponsesReceived)
                .add(jTextField_NumResponsesReceived));
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.CENTER)
                .add(jPanel2Layout.createSequentialGroup()
                    .add(jPanel2Layout.createParallelGroup()
                         .add(jLabel_Stratum)
                         .add(jTextField_Stratum))
                    .addContainerGap(10, 10)
                    .add(jPanel2Layout.createParallelGroup()
                         .add(jLabel_Poll)
                         .add(jTextField_Poll))
                    .addContainerGap(10, 10)
                    .add(jPanel2Layout.createParallelGroup()
                         .add(jLabel_Precision)
                         .add(jTextField_Precision))
                    .addContainerGap(10, 10)
                    .add(jPanel2Layout.createParallelGroup()
                        .add(jLabel_UNIX_Time)
                        .add(jTextField_UNIX_Time))
                    .addContainerGap(10, 10)
                    .add(jPanel2Layout.createParallelGroup()
                        .add(jLabel_UTC_Time)
                        .add(jTextField_UTC_Time))
                    .addContainerGap(10, 10)
                    .add(jLabel_NumRequestsSent)
                    .add(jTextField_NumRequestsSent)
                    .addContainerGap(10, 10)
                    .add(jLabel_NumResponsesReceived)
                    .add(jTextField_NumResponsesReceived)));
                
        jLabel_NIST_Servers = new JLabel();
        jLabel_NIST_Servers.setText("A selection of NIST servers are provided (availability may change over time)");
        jLabel_NTPServerURLs = new JLabel();
        jLabel_NTPServerURLs.setText("NTP Server URLs");
        jLabel_NTPServerLocations = new JLabel();
        jLabel_NTPServerLocations.setText("Location / description");
        jLabel_NTPServerStatus = new JLabel();
        jLabel_NTPServerStatus.setText("Status");
        jLabel_NTPServerOffset = new JLabel();
        jLabel_NTPServerOffset.setText("Local Offset");
        jLabel_NTPServerDelay = new JLabel();
        jLabel_NTPServerDelay.setText("RTT Delay");
        jLabel_NTPServerJitter = new JLabel();
        jLabel_NTPServerJitter.setText("Jittery");
        jList_NTPServerURLs = new JList(m_listModel_NTPServerList);
        jList_NTPServerURLs.setMaximumSize(new Dimension(300, 250));
        jList_NTPServerURLs.setSelectedIndex(0);
        jList_NTPServerURLs.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        jList_NTPServerURLs.addListSelectionListener(m_SelectionListener_NTPServerURLs);
        JScrollPane_NTPServerURLs = new javax.swing.JScrollPane(jList_NTPServerURLs,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, 
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        jList_NTPServerLocations = new JList(m_listModel_LocationList);
        jList_NTPServerLocations.setMaximumSize(new Dimension(260, 250));
        jList_NTPServerLocations.setSelectedIndex(0);
        jList_NTPServerLocations.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        jScrollPane_NTPServerLocations = new javax.swing.JScrollPane(jList_NTPServerLocations,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, 
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        // status
        jList_NTPServerStatus = new JList(m_listModel_StatusList);
        jList_NTPServerStatus.setMaximumSize(new Dimension(20, 250));
        jList_NTPServerStatus.setSelectedIndex(0);
        jList_NTPServerStatus.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        jScrollPane_NTPServerStatus = new javax.swing.JScrollPane(jList_NTPServerStatus,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        // offset
        jList_NTPServerOffset = new JList(m_listModel_OffsetList);
        jList_NTPServerOffset.setMaximumSize(new Dimension(10, 250));
        jList_NTPServerOffset.setSelectedIndex(0);
        jList_NTPServerOffset.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        jScrollPane_NTPServerOffset = new javax.swing.JScrollPane(jList_NTPServerOffset,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        // delay
        jList_NTPServerDelay = new JList(m_listModel_DelayList);
        jList_NTPServerDelay.setMaximumSize(new Dimension(10, 250));
        jList_NTPServerDelay.setSelectedIndex(0);
        jList_NTPServerDelay.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        jScrollPane_NTPServerDelay = new javax.swing.JScrollPane(jList_NTPServerDelay,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        // jitter
        jList_NTPServerJitter = new JList(m_listModel_JitterList);
        jList_NTPServerJitter.setMaximumSize(new Dimension(10, 250));
        jList_NTPServerJitter.setSelectedIndex(0);
        jList_NTPServerJitter.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        jScrollPane_NTPServerJitter = new javax.swing.JScrollPane(jList_NTPServerJitter,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        jPanel_NTPServerSelection = new JPanel();
        jPanel_NTPServerSelection.setPreferredSize(new Dimension(500, 300));
        jPanel_NTPServerSelection.setBorder(BorderFactory.createTitledBorder("NIST / NTP Server Selection"));
        org.jdesktop.layout.GroupLayout jPanel3Layout = new org.jdesktop.layout.GroupLayout(jPanel_NTPServerSelection);
        jPanel_NTPServerSelection.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.CENTER)
                .add(jPanel3Layout.createSequentialGroup()
                    .add(jLabel_NIST_Servers))
                .add(jPanel3Layout.createSequentialGroup()
                    .add(jPanel3Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.CENTER)
                    .add(jLabel_NTPServerURLs)
                    .add(JScrollPane_NTPServerURLs))
                    .add(jPanel3Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.CENTER)
                    .add(jLabel_NTPServerLocations)
                    .add(jScrollPane_NTPServerLocations))
                    .add(jPanel3Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.CENTER)
                    .add(jLabel_NTPServerStatus)
                    .add(jScrollPane_NTPServerStatus))
                    .add(jPanel3Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.CENTER)
                    .add(jLabel_NTPServerOffset)
                    .add(jScrollPane_NTPServerOffset))
                    .add(jPanel3Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.CENTER)
                    .add(jLabel_NTPServerDelay)
                    .add(jScrollPane_NTPServerDelay))
                    .add(jPanel3Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.CENTER)
                    .add(jLabel_NTPServerJitter)
                    .add(jScrollPane_NTPServerJitter))
                ));
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.CENTER)
                .add(jPanel3Layout.createSequentialGroup()
                    .add(jLabel_NIST_Servers)
                    .addContainerGap(20, 20)
                    .add(jPanel3Layout.createParallelGroup()
                        .add(jPanel3Layout.createSequentialGroup()
                            .add(jLabel_NTPServerURLs)
                            .add(JScrollPane_NTPServerURLs))
                        .add(jPanel3Layout.createSequentialGroup()
                               .add(jLabel_NTPServerLocations)
                               .add(jScrollPane_NTPServerLocations))
                        .add(jPanel3Layout.createSequentialGroup()
                            .add(jLabel_NTPServerStatus)
                            .add(jScrollPane_NTPServerStatus))
                        .add(jPanel3Layout.createSequentialGroup()
                            .add(jLabel_NTPServerOffset)
                            .add(jScrollPane_NTPServerOffset))
                        .add(jPanel3Layout.createSequentialGroup()
                            .add(jLabel_NTPServerDelay)
                            .add(jScrollPane_NTPServerDelay))
                        .add(jPanel3Layout.createSequentialGroup()
                            .add(jLabel_NTPServerJitter)
                            .add(jScrollPane_NTPServerJitter))
                    )));
        // Add Source
        jTextField_URL_OR_IP = new JTextField();
        jTextField_URL_OR_IP.setMaximumSize(new Dimension(250, 4));
        jTextField_URL_OR_IP.setHorizontalAlignment(JTextField.CENTER);
        jTextField_Description = new JTextField();
        jTextField_Description.setMaximumSize(new Dimension(250, 4));
        jTextField_Description.setHorizontalAlignment(JTextField.CENTER);
        jButton_AddSource = new JButton();
        jButton_AddSource.setText("Add");
        jButton_AddSource.setMaximumSize(new Dimension(150, 4));
        jButton_AddSource.addActionListener(this);

        jPanel_AddSource = new JPanel();
        jPanel_AddSource.setPreferredSize(new Dimension(650, 4));
        jPanel_AddSource.setBorder(BorderFactory.createTitledBorder("Add Source"));
        org.jdesktop.layout.GroupLayout jPanelAddLayout = new org.jdesktop.layout.GroupLayout(jPanel_AddSource);
        jPanel_AddSource.setLayout(jPanelAddLayout);
        jPanelAddLayout.setHorizontalGroup(
                jPanelAddLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.CENTER)
                        .add(jPanelAddLayout.createSequentialGroup()
                                .add(jTextField_URL_OR_IP)
                                .add(jTextField_Description)
                                .add(jButton_AddSource)));
        jPanelAddLayout.setVerticalGroup(
                jPanelAddLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.CENTER)
                        .add(jPanelAddLayout.createParallelGroup()
                                .add(jTextField_URL_OR_IP)
                                .add(jTextField_Description)
                                .add(jButton_AddSource)));
        
        // Control
        jButton_StartNTPClient = new JButton();
        jButton_StartNTPClient.setText("START NTP requests");
        jButton_StartNTPClient.setMaximumSize(new Dimension(100, 4));
        jButton_StartNTPClient.addActionListener(this);
        jButton_Done = new JButton();
        jButton_Done.setText("Done");
        jButton_Done.setMaximumSize(new Dimension(100, 4));
        jButton_Done.addActionListener(this);

        jPanel_Controls = new JPanel();
        jPanel_Controls.setPreferredSize(new Dimension(500, 100));
        jPanel_Controls.setBorder(BorderFactory.createTitledBorder("Controls"));
        org.jdesktop.layout.GroupLayout jPanel4Layout = new org.jdesktop.layout.GroupLayout(jPanel_Controls);
        jPanel_Controls.setLayout(jPanel4Layout);
        jPanel4Layout.setHorizontalGroup(
            jPanel4Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.CENTER)
                .add(jPanel4Layout.createSequentialGroup()
                    .addContainerGap(200, 200)
                    .add(jButton_StartNTPClient)
                    .addContainerGap(200, 200)
                    .add(jButton_Done)
                    .addContainerGap(200, 200)));
        jPanel4Layout.setVerticalGroup(
            jPanel4Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.CENTER)
                .add(jPanel4Layout.createParallelGroup()
                    .add(jButton_StartNTPClient)
                    .add(jButton_Done)));

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        getContentPane().setPreferredSize(new Dimension(1000, 700));
         
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.CENTER)
            .add(layout.createSequentialGroup()
                .add(layout.createParallelGroup()            
                    .add(jPanel_NTPServerAddressDetails)
                    .add(jPanel_Time_Status))
                .add(layout.createParallelGroup()            
                    .add(jPanel_NTPServerSelection)
                    .add(jPanel_AddSource)
                    .add(jPanel_Controls))));
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.CENTER)
            .add(layout.createSequentialGroup()
                .add(jPanel_NTPServerAddressDetails)
                .addContainerGap(20, 20)
                .add(jPanel_Time_Status))
            .add(layout.createSequentialGroup()
                .add(jPanel_NTPServerSelection)
                .addContainerGap(20, 20)
                .add(jPanel_AddSource)
                .addContainerGap(20, 20)
                .add(jPanel_Controls)));

        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setTitle("Network Time Protocol client");
        pack();
    }                                             
}