package com.example.msvc_ventas;

import com.example.msvc_ventas.domain.service.PagoService;
import com.example.msvc_ventas.domain.service.StripeService;
import com.example.msvc_ventas.presentation.controller.StripeController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.lang.reflect.Modifier;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stripe queda INACTIVO por decisión del proyecto: sin stripe.enabled=true no existe ni el controlador
 * ni el servicio (no se necesitan claves) y ningún flujo de Stripe puede aprobar pagos.
 */
class StripeDeshabilitadoTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(StripeService.class, StripeController.class);

    @Test
    void porDefectoNoSeRegistraNingunComponenteDeStripe() {
        runner.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).doesNotHaveBean(StripeController.class);
            assertThat(ctx).doesNotHaveBean(StripeService.class);
        });
    }

    @Test
    void conLaBanderaEnFalseTampoco() {
        runner.withPropertyValues("stripe.enabled=false", "stripe.api.key=sk_test_x").run(ctx -> {
            assertThat(ctx).doesNotHaveBean(StripeController.class);
            assertThat(ctx).doesNotHaveBean(StripeService.class);
        });
    }

    @Test
    void activarloSinClaveFallaAlArrancar() {
        runner.withPropertyValues("stripe.enabled=true").run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void activadoExplicitamenteSeRegistra() {
        runner.withPropertyValues("stripe.enabled=true", "stripe.api.key=sk_test_x").run(ctx -> {
            assertThat(ctx).hasSingleBean(StripeController.class);
            assertThat(ctx).hasSingleBean(StripeService.class);
        });
    }

    @Test
    void elWebhookSinFirmaYElAtajoQueCompletabaVentasYaNoExisten() {
        assertThat(Arrays.stream(StripeController.class.getDeclaredMethods()).map(m -> m.getName()))
                .doesNotContain("handleWebhook");
        assertThat(Arrays.stream(PagoService.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .map(m -> m.getName()))
                .doesNotContain("procesarPagoTarjeta");
    }
}
