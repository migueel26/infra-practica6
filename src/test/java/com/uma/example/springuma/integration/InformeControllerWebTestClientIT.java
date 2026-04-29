package com.uma.example.springuma.integration;

import java.nio.file.Paths;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;

import com.uma.example.springuma.integration.base.AbstractIntegration;
import com.uma.example.springuma.model.Imagen;
import com.uma.example.springuma.model.Informe;
import com.uma.example.springuma.model.Medico;
import com.uma.example.springuma.model.Paciente;

import jakarta.annotation.PostConstruct;
import reactor.core.publisher.Mono;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class InformeControllerWebTestClientIT extends AbstractIntegration {

    @LocalServerPort
    private Integer port;

    private WebTestClient testClient;

    private Medico medico;
    private Paciente paciente;
    private Imagen imagen;
    private Informe informe;

    @PostConstruct
    public void init() {
        testClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofMillis(300000)).build();
    }

    @BeforeEach
    void setUp() {

        medico = new Medico();
        medico.setNombre("Miguel");
        medico.setId(1L);
        medico.setDni("835");
        medico.setEspecialidad("Ginecologo");

        paciente = new Paciente();
        paciente.setId(1L);
        paciente.setNombre("Maria");
        paciente.setDni("888");
        paciente.setEdad(20);
        paciente.setCita("Ginecologia");
        paciente.setMedico(medico);

        imagen = new Imagen();
        imagen.setId(1L);
        imagen.setPaciente(paciente);

        // Crea médico
        testClient.post().uri("/medico")
                .body(Mono.just(medico), Medico.class)
                .exchange()
                .expectStatus().isCreated();

        // Crea paciente
        testClient.post().uri("/paciente")
                .body(Mono.just(paciente), Paciente.class)
                .exchange()
                .expectStatus().isCreated();

        // Crea imagen
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("image", new FileSystemResource(Paths.get("src/test/resources/healthy.png").toFile()));
        builder.part("paciente", paciente);

        testClient.post().uri("/imagen")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isOk();

    }

    @Test
    @DisplayName("Crear informe y obtenerlo por la ID de la imagen")
    void crearInformeYObtenerPorImagen() {
        // Recuperamos la imagen
        Imagen uploadedImage = testClient.get().uri("/imagen/paciente/" + paciente.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Imagen.class)
                .returnResult().getResponseBody().get(0);

        Informe nuevoInforme = new Informe();
        nuevoInforme.setContenido("No se aprecian anomalías claras en la mamografía.");
        nuevoInforme.setImagen(uploadedImage);

        // Crear informe
        testClient.post().uri("/informe")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(nuevoInforme), Informe.class)
                .exchange()
                .expectStatus().isCreated();

        // Obtener informes de la imagen
        testClient.get().uri("/informe/imagen/" + uploadedImage.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Informe.class)
                .hasSize(1)
                .consumeWith(response -> {
                    Informe informeResult = response.getResponseBody().get(0);
                    assertTrue("No se aprecian anomalías claras en la mamografía.".equals(informeResult.getContenido()));
                    assertTrue(informeResult.getPrediccion() != null); // Comprobar que le ha asignado la predicción
                });
    }

    @Test
    @DisplayName("Eliminar un informe")
    void eliminarInforme() {
        Imagen uploadedImage = testClient.get().uri("/imagen/paciente/" + paciente.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Imagen.class)
                .returnResult().getResponseBody().get(0);

        Informe nuevoInforme = new Informe();
        nuevoInforme.setContenido("Informe listo para eliminar");
        nuevoInforme.setImagen(uploadedImage);

        testClient.post().uri("/informe")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(nuevoInforme), Informe.class)
                .exchange()
                .expectStatus().isCreated();

        Informe informeCreado = testClient.get().uri("/informe/imagen/" + uploadedImage.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Informe.class)
                .returnResult().getResponseBody().get(0);

        testClient.delete().uri("/informe/" + informeCreado.getId())
                .exchange()
                .expectStatus().isNoContent();
    }
}
