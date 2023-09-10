This is our TCP Socket FTP Server

Seth Nye, Davis Webster, David Korsunsky

In order to compile and run:

Start by going into the FTP-Server directory and run the following command: "javac -d bin src/myftpserver.java"

Go into the FTP-Client directory and run the following command: "javac -d bin src/myftp.java"

In seperate nodes, you are free to run the programs, one for the server, others for the clients

We recommend pinging the vcf node that has the server assigned to it and copying that IP address before running any program

Now type on the server side: "java -cp bin myserverftp 'port#' 'terminate#' " where 'port#' is a number for your port and 'terminate #' is the terminate port

Go to the other vcf for your client(s) and enter : "java -d bin myftp 'hostname' 'port#' 'terminate #' " where 'hostname' is the copied IP address and 'port#'/'terminate#' is the same number as the server's

From here your client should connect to the server and be able to start writing commands. You can also run the execute command for clients multiple times if more than one client are trying to connect

“ This project was done in its entirety by David Korsunsky, Davis Webster, and Seth Nye. We hereby state that we have not received unauthorized help of any form ”