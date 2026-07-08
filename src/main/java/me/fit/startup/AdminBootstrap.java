package me.fit.startup;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import me.fit.service.UserService;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.Optional;

// Ako je postavljen ADMIN_EMAIL, korisnik sa tom adresom se pri pokretanju
// unaprijedi u ADMIN-a (idempotentno). Tako se admin ne seeduje sa poznatom
// lozinkom, nego vlastiti nalog dobija admin ulogu.
@ApplicationScoped
public class AdminBootstrap {

    private static final Logger LOG = Logger.getLogger(AdminBootstrap.class);

    @Inject
    UserService userService;

    @ConfigProperty(name = "app.admin.email")
    Optional<String> adminEmail;

    void onStart(@Observes StartupEvent event) {
        adminEmail.map(String::trim).filter(email -> !email.isEmpty()).ifPresent(email -> {
            userService.promoteToAdmin(email);
            LOG.infof("Postavljanje ADMIN uloge za %s (ako nalog postoji)", email);
        });
    }
}
