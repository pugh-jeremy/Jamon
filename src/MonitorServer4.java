/*
these 2 packages are required for this app and you need to set your classpath to include their jar files after download
http://www.oracle.com/technetwork/java/javasebusiness/downloads/java-archive-downloads-eeplat-419426.html#javamail-1.4-oth-JPR
http://www.oracle.com/technetwork/java/javasebusiness/downloads/java-archive-downloads-java-plat-419418.html#jaf-1.1.1-fcs-oth-JPR

common usage: java MonitorServer 9898

C:/Windows/Temp/health.txt stores ping and port check results that can be done from the local server.  
These are done via the LocalMonitor class below

C:/Windows/Temp/healthRemote.txt stores memory/hd/cpu usage% which depends on remote client to connect to this server.
These checks are done via the Monitor class which launches the socket that listens for client connections

C:/Windows/Temp/checklist.txt stores the local server (results written to health.txt) checks the user wants the service to perform
Here is example contents of a valid checklist.txt file:
mainDB:4.34.95.53
mainDB:4.34.95.53:8000
mainDB:4.34.95.53:10281  
mainDB:4.34.95.53:10322
mainDB:4.34.95.53:10222
mainDB:4.34.95.53:22
mainDB:4.34.95.53:333
failoverDB:4.34.95.51
failoverDB:4.34.95.51:22
GWR3:4.34.95.49

*/

import java.io.*;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

import java.net.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import java.util.Scanner;
import java.util.Date;
import java.util.Properties;

import java.text.SimpleDateFormat;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;

import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart; 
import javax.mail.Authenticator;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;

//take out clientNumber and fix and close any sockets/files that need closing
// try moving instantiation outside of LocalMonitor run() and inside LocalMonitor class, understand the differences
// log errors and connections to monitorlog.txt
// read ips and ports to ping/connect to from File
// break the try/catch sections of LocalMonitor into smaller portions with logging functionality


 
public class MonitorServer4 {
    public static void main(String[] args) throws IOException, InterruptedException {
                    

      System.out.println("The monitor server is running.");
      int clientNumber = 0;
      ServerSocket listener = new ServerSocket(9898);

      //probably bad design, but we launch the local monitor thread and it calls itself in the thread run method
      new LocalMonitor().start();        
      while (true) {    
        new Monitor(listener.accept(), clientNumber++).start();                  
      }// ends while (true)
    }//ends main method


    public static class LocalMonitor extends Thread  {       
      public LocalMonitor() {
      
      }   

      public void run() {
	SimpleDateFormat sdf = new SimpleDateFormat("dd-M-yyyy hh:mm:ss");
        String date = sdf.format(new Date()); 
        System.out.println(date);

        try {
          File file = new File("C:/Windows/Temp/health.txt");
          PrintWriter fileOutput = new PrintWriter(file);
          Scanner input = new Scanner(file);

          //as the method below states, this will support reading checks from file as opposed to hard coding
          //however, this method is currently not implemented         
          readCheckList();
         
          //run ping/port check tests and write results to health.txt file 
          runChecks(fileOutput);        

          fileOutput.flush();
          fileOutput.close();
                        
          String emailText = " ";
          String line = "";
          //parse contents of health file and alert if ping/port checks failed (line contains false)
          while (input.hasNext()) {
            line = input.next();
            if (line.contains("false"))
              emailText = emailText + "\n" + line;
          } 

          //parse contents of healthRemote file and alert if over threshhold
          File fileRemote = new File("C:/Windows/Temp/healthRemote.txt");
          Scanner inputRemote = new Scanner(fileRemote);
          String healthStat = "";
          String descriptor = "";
          Float value = null;
          while (inputRemote.hasNext()) {
            healthStat = inputRemote.next();
            Scanner health = new Scanner(healthStat);
            health.useDelimiter(":");
            while (health.hasNext()) {
              descriptor = health.next();
              value = health.nextFloat();
              if (value >= 90) {
                //System.out.println(descriptor + ":" + value + ",result:false");
                emailText = emailText + "\n" + descriptor + ":" + value + ",result:false"; 
              }
            }//ends inner while
          }//ends outer while

          inputRemote.close();
        
          System.out.println(emailText);
          if (emailText != " ") {
            final String toEmail = "jeremy.pugh@adrevolution.com"; // can be any email id 
            //I'm sending the test to jeremy.pugh@adrevolution.com from jeremy.pugh@adrevolution.com so it doesn't junk
            sendEmail(establishEmailSession(toEmail), toEmail, "Alert - System Issue", emailText);
          }//ends if
          } catch (IOException e) {
            //logging to add in future log("Error handling client# " + clientNumber + ": " + e);
          }
        
          try {
            //regardless of whether an alert email was sent, we want to zero out the healthRemote file
            File fileRemote = new File("C:/Windows/Temp/healthRemote.txt");			
            PrintWriter fileOutputRemote = new PrintWriter(fileRemote);
            fileOutputRemote.close();
          } catch (IOException e) {
            //logging to add in future log("Error handling client# " + clientNumber + ": " + e);
          }             


          try {
            Thread.sleep(30000); //check stats every 10 minutes  
          } catch(InterruptedException e) {
            //donothing about it
          }

          //spawn a new thread of myself
          new LocalMonitor().start();   

      }//ends run method

      //this is to support checks from a file as opposed to hard coded
      //this code is currently not used
      public static void readCheckList () {
        try {
          File checkListFile = new File("C:/Windows/Temp/checklist.txt");
          Scanner checkList = new Scanner(checkListFile);
          
          String lineItem = "";
          String ip = "";
          String port = "";
          String host = "";
          int delimiterCount = 0;
          while (checkList.hasNext()) {
            lineItem = checkList.next();
            String[] values = lineItem.split(":", -1);
            delimiterCount = values.length;

            Scanner item = new Scanner(lineItem);
            item.useDelimiter(":");

            //parse lines with hostname and IP
            if (delimiterCount == 2) { 
              while (item.hasNext()) {
                host = item.next();
                ip = item.next();
                //port = item.next();
                System.out.println("pinging ip: " + ip + " for host " + host);
              }//ends inner while
            }//ends if

            //parse lines with hostname, ip, and port
            if (delimiterCount == 3) { 
              while (item.hasNext()) {
                host = item.next();
                ip = item.next();
                port = item.next();
                if (port != null) {
                  System.out.println("checking port: " + port + " on ip " + ip + " for host " + host);
                }
              }//ends inner while
            }//ends if
 
          }//ends outer while

          
          checkList.close();          
          
        } catch (IOException e) {
          System.out.println(e);
          //logging to add in future log("Error handling client# " + clientNumber + ": " + e);
        } //ends catch
      }// ends readCheckList method

      public void runChecks(PrintWriter fileOutput) {
        Boolean result;
        result=checkPing("4.34.95.53");
        fileOutput.print("pinging-main-DB-4.34.95.53,result:");
        fileOutput.println(result);
        fileOutput.println("4.34.95.53:8000,result:" + checkPort("4.34.95.53",8000));
        fileOutput.println("4.34.95.53:10281,result:" + checkPort("4.34.95.53",10281));  
        fileOutput.println("4.34.95.53:10322,result:" + checkPort("4.34.95.53",10322));
        fileOutput.println("4.34.95.53:10222,result:" + checkPort("4.34.95.53",10222));
        fileOutput.println("4.34.95.53:22,result:" + checkPort("4.34.95.53",22));
        //fileOutput.println("4.34.95.53:333,result:" + checkPort("4.34.95.53",333));

        result=checkPing("4.34.95.51");
        fileOutput.print("pinging-backup-server-4.34.95.51,result:");
        fileOutput.println(result);
        fileOutput.println("4.34.95.51:22,result:" + checkPort("4.34.95.51",22));

        result=checkPing("4.34.95.49");
        fileOutput.print("pinging-router-GWR3-4.34.95.49,result:");
        fileOutput.println(result);
      } //ends runChecks method

    }//ends LocalMonitor class


    public static class Monitor extends Thread {
      private Socket socket;
      private int clientNumber;

      public Monitor(Socket socket, int clientNumber) {
        this.socket = socket;
        this.clientNumber = clientNumber;
      }

      public void run() {
        try {
          // Decorate the streams so we can send characters
          // and not just bytes.  Ensure output is flushed
          // after every newline.
          BufferedReader in = new BufferedReader(
                 new InputStreamReader(socket.getInputStream()));
          //PrintWriter out = new PrintWriter(socket.getOutputStream(), true); //in case I want to tell client something later
          File file = new File("C:/Windows/Temp/healthRemote.txt");			
          PrintWriter fileOutput = new PrintWriter(new FileWriter(file, true));

          //out.println("Enter a line with only a period to quit\n");

          // Get messages from the client, line by line and write to healthRemote.txt
          while (true) {
            String input = in.readLine();
            if (input == null || input.equals(".")) {
              break;
            }
            fileOutput.println(input);
            fileOutput.flush();           
          }// ends while (true)
  
          fileOutput.close();

        } catch (IOException e) {
          //log("Error handling client# " + clientNumber + ": " + e);
        }finally {
          try {
            socket.close();
          } catch (IOException e) {
            //    log("Couldn't close a socket, what's going on?");
          }
            //log("Connection with client# " + clientNumber + " closed");
        } //ends finally
      } //ends run method
    }//ends Monitor class
      

    public static boolean checkPing(String host) {
      try{
        String cmd = "";
        if(System.getProperty("os.name").startsWith("Windows")) {   
          // For Windows
          cmd = "ping -n 2 -w 1000 " + host;
        } 

        Process myProcess = Runtime.getRuntime().exec(cmd);
        myProcess.waitFor();

        if(myProcess.exitValue() == 0) {
          return true;
        } 
        else {
          return false;
        }
      } catch( Exception e ) {
        e.printStackTrace();
        return false;
      }
    }//ends checkPing method
    
    public static boolean checkPort(String host, int port) {
      int exitStatus = 1 ;  
      int timeout = 3;

      Socket s = null;
      String reason = null;
      try {
        s = new Socket();
        s.setReuseAddress(true);
        SocketAddress sa = new InetSocketAddress(host, port);
        s.connect(sa, timeout * 1000);
      } catch (IOException e) {
        if ( e.getMessage().equals("Connection refused")) {
          reason = "port " + port + " on " + host + " is closed.";
        };
        if ( e instanceof UnknownHostException ) {
          reason = "node " + host + " is unresolved.";
        }
        if ( e instanceof SocketTimeoutException ) {
          reason = "timeout while attempting to reach node " + host + " on port " + port;
        }
      } finally {
        if (s != null) {
          if ( s.isConnected()) {
            //System.out.print("Port " + port + " on " + host + " is reachable!");
            exitStatus = 0;
            return true;
          } else {
            //System.out.print("Port " + port + " on " + host + " is not reachable; reason: " + reason );
          }
          try {
            s.close();
          } catch (IOException e) {
            //nothing here
          }
        }
      }
      return false;
    } //ends checkPort method


    public static Session establishEmailSession(String toEmail){
      //this.toEmail = toEmail;
      final String fromEmail = "jeremy.pugh@adrevolution.com"; //requires valid gmail id, this is monitoring account email address
      final String password = "CuW<Yuq9"; // correct password for gmail id, this is monitoring account email address

      Properties props = new Properties();
      props.put("mail.smtp.host", "smtp.gmail.com"); //SMTP Host
      props.put("mail.smtp.port", "587"); //TLS Port
      props.put("mail.smtp.auth", "true"); //enable authentication
      props.put("mail.smtp.starttls.enable", "true"); //enable STARTTLS
      //create Authenticator object to pass in Session.getInstance argument
      Authenticator auth = new Authenticator() {
        //override the getPasswordAuthentication method
        protected PasswordAuthentication getPasswordAuthentication() {
          return new PasswordAuthentication(fromEmail, password);
        }
      };
      Session session = Session.getInstance(props, auth);
      return session;
    }//ends the establishEmailSession method

    public static void sendEmail(Session session, String toEmail, String subject, String body){
      try {
        MimeMessage msg = new MimeMessage(session);
        //set message headers
        msg.addHeader("Content-type", "text/HTML; charset=UTF-8");
        msg.addHeader("format", "flowed");
        msg.addHeader("Content-Transfer-Encoding", "8bit");
        msg.setFrom(new InternetAddress("monitor@careerandjobtipsvps.com", "NoReply-CAJT-VPS")); 
        msg.setReplyTo(InternetAddress.parse("no_reply@careerandjobtips.com", false));
        msg.setSubject(subject, "UTF-8"); 
        msg.setText(body, "UTF-8");
        msg.setSentDate(new Date()); 
        msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail, false));
        Transport.send(msg);  
        System.out.println("EMail Sent Successfully!!");
      }
      catch (Exception e) {
        e.printStackTrace();
      }
    }//ends public static void sendEmail
}//ends MonitorServer Class
