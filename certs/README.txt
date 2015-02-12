Easiest thing to do is to add it to the cacerts file;

C:\Program Files\Java\jdk1.8.0_20\bin>keytool -import -alias "Brown" -keystore .
.\jre\lib\security\cacerts -file \users\meekse\Development\Eclipse\workspace\Crossli
nks\certs\brown.cer

password = changeit