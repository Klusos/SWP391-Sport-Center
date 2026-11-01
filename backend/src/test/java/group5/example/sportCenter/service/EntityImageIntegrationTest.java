package group5.example.sportCenter.service;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;

import group5.example.sportCenter.entity.Coach;
import group5.example.sportCenter.entity.Facility;
import group5.example.sportCenter.entity.Sport;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.repository.CoachRepository;
import group5.example.sportCenter.repository.FacilityRepository;
import group5.example.sportCenter.repository.SportRepository;
import group5.example.sportCenter.repository.UserRepository;
import group5.example.sportCenter.security.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "spring.jpa.open-in-view=false")
@ActiveProfiles("phase1")
class EntityImageIntegrationTest {
    @Autowired private WebApplicationContext context;
    @Autowired private FilterChainProxy security;
    @Autowired private UserRepository users;
    @Autowired private SportRepository sports;
    @Autowired private CoachRepository coaches;
    @Autowired private FacilityRepository facilities;
    @Autowired private JwtService tokens;
    @Autowired private LocalImageStorageService storage;
    @Autowired private ObjectMapper mapper;

    private MockMvc mvc;
    private final List<String> uploadedUrls = new ArrayList<>();
    private User admin;
    private User member;
    private User receptionist;
    private Sport sport;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
        admin = user("ADMIN");
        member = user("MEMBER");
        receptionist = user("RECEPTIONIST");
        sport = new Sport();
        sport.setName("Image Sport " + UUID.randomUUID());
        sport.setStatus("ACTIVE");
        sport = sports.saveAndFlush(sport);
    }

    @AfterEach
    void removeTestFiles() {
        uploadedUrls.forEach(storage::deleteStoredUrl);
    }

    @Test
    void adminCanUploadRetrieveAndReplaceImagesForSportCoachAndFacility() throws Exception {
        MvcResult sportUpload = upload("/api/sports/" + sport.getId() + "/image", png());
        String sportUrl = imageUrl(sportUpload);
        uploadedUrls.add(sportUrl);
        mvc.perform(get(sportUrl)).andExpect(status().isOk()).andExpect(content().contentType("image/png"));
        mvc.perform(get("/api/public/sports")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + sport.getId() + ")].imageUrl").value(sportUrl));

        Coach coach = new Coach();
        coach.setUser(user("COACH"));
        coach.setStatus("ACTIVE");
        coach = coaches.saveAndFlush(coach);
        String coachUrl = imageUrl(upload("/api/coaches/" + coach.getId() + "/image", png()));
        uploadedUrls.add(coachUrl);
        mvc.perform(get("/api/public/coaches")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + coach.getId() + ")].imageUrl").value(coachUrl))
                .andExpect(jsonPath("$[?(@.id == " + coach.getId() + ")].email").doesNotExist());

        Facility facility = new Facility();
        facility.setSport(sport);
        facility.setSports(new java.util.LinkedHashSet<>(List.of(sport)));
        facility.setName("Image Facility " + UUID.randomUUID());
        facility.setType("STUDIO");
        facility.setStatus("ACTIVE");
        facility = facilities.saveAndFlush(facility);
        String facilityUrl = imageUrl(upload("/api/facilities/" + facility.getId() + "/image", png()));
        uploadedUrls.add(facilityUrl);
        mvc.perform(get("/api/public/facilities")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + facility.getId() + ")].imageUrl").value(facilityUrl));

        String replacement = imageUrl(upload("/api/sports/" + sport.getId() + "/image", jpeg()));
        uploadedUrls.add(replacement);
        mvc.perform(get(sportUrl)).andExpect(status().isNotFound());
        mvc.perform(get(replacement)).andExpect(status().isOk()).andExpect(content().contentType("image/jpeg"));
    }

    @Test
    void uploadRequiresAdminAndExistingTargetAndRejectsInvalidContent() throws Exception {
        mvc.perform(multipart("/api/sports/{id}/image", sport.getId()).file(file(png())).with(putMethod()))
                .andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/sports/{id}/image", sport.getId()).file(file(png())).with(putMethod())
                        .header("Authorization", bearer(member)))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/sports/{id}/image", sport.getId()).file(file(png())).with(putMethod())
                        .header("Authorization", bearer(receptionist)))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/sports/{id}/image", 999999).file(file(png())).with(putMethod())
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isNotFound());
        mvc.perform(multipart("/api/coaches/{id}/image", 999999).file(file(png())).with(putMethod())
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isNotFound());
        mvc.perform(multipart("/api/facilities/{id}/image", 999999).file(file(png())).with(putMethod())
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isNotFound());
        mvc.perform(multipart("/api/sports/{id}/image", sport.getId())
                        .file(new MockMultipartFile("file", "x.png", "image/png", "not an image".getBytes()))
                        .with(putMethod()).header("Authorization", bearer(admin)))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/sports/{id}/image", sport.getId())
                        .file(new MockMultipartFile("file", "large.png", "image/png", new byte[5 * 1024 * 1024 + 1]))
                        .with(putMethod()).header("Authorization", bearer(admin)))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void publicImageAndCatalogReadsDoNotRequireAuthentication() throws Exception {
        mvc.perform(get("/api/public/coaches")).andExpect(status().isOk());
        mvc.perform(get("/api/public/facilities")).andExpect(status().isOk());
        mvc.perform(get("/api/public/images/../../application.properties")).andExpect(status().is4xxClientError());
    }

    private MvcResult upload(String path, byte[] content) throws Exception {
        return mvc.perform(multipart(path).file(file(content)).with(putMethod()).header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.imageUrl").isString()).andReturn();
    }

    private String imageUrl(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString()).get("imageUrl").stringValue();
    }

    private MockMultipartFile file(byte[] content) {
        return new MockMultipartFile("file", "arbitrary-client-name.png", "text/plain", content);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor putMethod() {
        return request -> { request.setMethod("PUT"); return request; };
    }

    private byte[] png() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private byte[] jpeg() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "jpeg", output);
        return output.toByteArray();
    }

    private User user(String role) {
        User user = new User();
        user.setName("Image API " + role);
        user.setEmail(UUID.randomUUID() + "@example.test");
        user.setPasswordHash("not-used-in-test");
        user.setRole(role);
        user.setStatus("ACTIVE");
        return users.saveAndFlush(user);
    }

    private String bearer(User user) { return "Bearer " + tokens.generateToken(user); }
}
