package me.fit.service;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

// Slanje transakcionih poruka (reset lozinke, verifikacija emaila).
// U dev/test/demo profilu mailer je mock: poruke se ne salju vec se link
// ispisuje u log kako bi se tok mogao isprobati. U pravom prod-u sa SMTP-om
// se link ne loguje.
@ApplicationScoped
public class EmailService {

    private static final Logger LOG = Logger.getLogger(EmailService.class);

    @Inject
    Mailer mailer;

    @ConfigProperty(name = "app.base-url", defaultValue = "http://localhost:8080")
    String baseUrl;

    @ConfigProperty(name = "quarkus.mailer.mock", defaultValue = "false")
    boolean mailerMock;

    public void sendPasswordReset(String toEmail, String name, String rawToken) {
        String link = baseUrl + "/?reset=" + rawToken;
        String body = "Zdravo " + name + ",\n\n"
                + "Zatražili ste resetovanje lozinke za nalog na Moje finansije.\n"
                + "Otvorite sljedeći link da postavite novu lozinku:\n\n"
                + link + "\n\n"
                + "Link vrijedi 30 minuta. Ako niste vi poslali zahtjev, slobodno zanemarite ovu poruku.\n";
        send(toEmail, "Resetovanje lozinke — Moje finansije", body, link);
    }

    public void sendVerification(String toEmail, String name, String rawToken) {
        String link = baseUrl + "/?verify=" + rawToken;
        String body = "Zdravo " + name + ",\n\n"
                + "Dobrodošli na Moje finansije! Potvrdite svoju email adresu klikom na link:\n\n"
                + link + "\n\n"
                + "Ako niste otvorili nalog, slobodno zanemarite ovu poruku.\n";
        send(toEmail, "Potvrda email adrese — Moje finansije", body, link);
    }

    private void send(String toEmail, String subject, String body, String link) {
        mailer.send(Mail.withText(toEmail, subject, body));
        if (mailerMock) {
            LOG.infof("[mock email] za %s — link: %s", toEmail, link);
        }
    }
}
