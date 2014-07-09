/*
these 2 packages are required for this app and you need to set your classpath to include their jar files after download
http://www.oracle.com/technetwork/java/javasebusiness/downloads/java-archive-downloads-eeplat-419426.html#javamail-1.4-oth-JPR
http://www.oracle.com/technetwork/java/javasebusiness/downloads/java-archive-downloads-java-plat-419418.html#jaf-1.1.1-fcs-oth-JPR

common usage: java MonitorServer 9090
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
 
public class MonitorServer {
    public static void main(String[] args) throws IOException, InterruptedException {
      while (true) {    
        if (args.length != 1) {
          System.err.println("Usage: java EchoServer <port number>");
          System.exit(1);
        }
         
        int portNumber = Integer.parseInt(args[0]);
 
        File file = new File("C:/Windows/Temp/health.txt");
        PrintWriter fileOutput = new PrintWriter(file);

        Boolean result;
        result=checkPing("4.34.95.53");
        fileOutput.print("pinging-main-DB-4.34.95.53,result:");
        fileOutput.println(result);
        fileOutput.println("4.34.95.53:8000,result:" + checkPort("4.34.95.53",8000));
        fileOutput.println("4.34.95.53:10281,result:" + checkPort("4.34.95.53",10281));  
        fileOutput.println("4.34.95.53:10322,result:" + checkPort("4.34.95.53",10322));
        fileOutput.println("4.34.95.53:10222,result:" + checkPort("4.34.95.53",10222));
        fileOutput.println("4.34.95.53:22,result:" + checkPort("4.34.95.53",22));

        result=checkPing("4.34.95.51");
        fileOutput.print("pinging-backup-server-4.34.95.51,result:");
        fileOutput.println(result);
        fileOutput.println("4.34.95.51:22,result:" + checkPort("4.34.95.51",22));

        result=checkPing("4.34.95.53");
        fileOutput.print("pinging-router-GWR3-4.34.95.49,result:");
        fileOutput.println(result);

        fileOutput.flush();



        final String fromEmail = "jeremy.pugh@adrevolution.com"; //requires valid gmail id
        final String password = "CuW<Yuq9"; // correct password for gmail id
        final String toEmail = "jeremy.pugh@adrevolution.com"; // can be any email id 
         
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


        Scanner input = new Scanner(file);
        String emailText = " ";
        String line = "";
        while (input.hasNext()) {
          line = input.next();
          if (line.contains("false"))
            emailText = emailText + "\n" + line;
        }


	SimpleDateFormat sdf = new SimpleDateFormat("dd-M-yyyy hh:mm:ss");
	String date = sdf.format(new Date()); 
	System.out.println(date);


        System.out.println(emailText);
        if (emailText != " ")
          sendEmail(session, toEmail, "Alert - System Issue", emailText);

        //System.out.println("My thread is tired from all the work and going to bed now");
        Thread.sleep(600000); //check stats every 10 minutes   
        //System.out.println("Wakey, Wakey, threads come save me!");
         

      }// ends while (true)
                   
/*          try (
            ServerSocket serverSocket =
                new ServerSocket(Integer.parseInt(args[0]));
            Socket clientSocket = serverSocket.accept();     
            PrintWriter out =
                new PrintWriter(clientSocket.getOutputStream(), true);                   
            BufferedReader in = new BufferedReader(
                new InputStreamReader(clientSocket.getInputStream()));
          ) {
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                out.println(inputLine);
                fileOutput.println(inputLine);
                if ("exit".equalsIgnoreCase(inputLine)) {
                  fileOutput.flush();
                  in.close();
                  clientSocket.close();
                }
            }				
          } catch (IOException e) {
            System.out.println("Exception caught when trying to listen on port "
                + portNumber + " or listening for a connection");
            System.out.println(e.getMessage());
          }
  */   
        //}//ends while (true)
   
    }//ends main method

    public static boolean checkPing(String host) {
      try{
              String cmd = "";
              if(System.getProperty("os.name").startsWith("Windows")) {   
                      // For Windows
                      cmd = "ping -n 2 -w 1000 " + host;
              } 
              //else {
                      // For Linux and OSX
              //        cmd = "ping -c 1 " + host;
              //}

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
    }
    
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
          }
        }
      }
      return false;
    }



    public static void sendEmail(Session session, String toEmail, String subject, String body){



        try
        {
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
          //System.out.println("Message is ready");
          Transport.send(msg);  
 
          System.out.println("EMail Sent Successfully!!");
        }
        catch (Exception e) {
          e.printStackTrace();
        }
    }//ends public static void sendEmail



}//ends MonitorServer Class
