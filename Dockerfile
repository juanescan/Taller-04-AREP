FROM openjdk:17
WORKDIR /usrapp/bin
ENV PORT 8080
COPY target/classes /usrapp/bin/classes
COPY src/main/webapp /usrapp/bin/src/main/webapp
CMD ["java","-cp","./classes:./dependency/*","eci.arep.juancancelado.microspringboot.MicroSpringBoot"]
