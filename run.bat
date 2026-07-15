# Maven compilacion

mvn clean package
mvn -Pdemo-executable -pl notifications-demo -am package

# Helper de consumo
java -jar './notifications-demo/target/notifications-demo-1.0.0.jar'

# Consumo
java -jar ./notifications-demo/target/notifications-demo-1.0.0.jar --channel=email --to=lrivera@bigfoot.net --subject=Start_Account --message="Pruebas de bienestar"

# Multi canal
java -cp ./notifications-demo/target/notifications-demo-1.0.0.jar com.demo.notifications.examples.NotificationMultichannelMain

# Consumo desde Resiliencia simula (fail over y retry)
java -cp notifications-demo\target\notifications-demo-1.0.0.jar com.demo.notifications.examples.NotificationResilientMain --channel=email --to=user@example.com --subject=Bienvenido --message="Tu cuenta esta lista." --demo-failover

# Consumo desde Resiliencia simula circuit breaker open
java -cp notifications-demo\target\notifications-demo-1.0.0.jar com.demo.notifications.examples.NotificationResilientMain --channel=email --to=user@example.com --subject=Bienvenido --message="Tu cuenta esta lista." --demo-circuit-breaker

# Consumo nuevo canal
