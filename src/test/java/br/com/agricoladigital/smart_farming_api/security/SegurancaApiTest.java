package br.com.agricoladigital.smart_farming_api.security;

import br.com.agricoladigital.smart_farming_api.config.CorsConfig;
import br.com.agricoladigital.smart_farming_api.config.JwtConfig;
import br.com.agricoladigital.smart_farming_api.config.SecurityConfig;
import br.com.agricoladigital.smart_farming_api.controller.AuthController;
import br.com.agricoladigital.smart_farming_api.controller.CulturaController;
import br.com.agricoladigital.smart_farming_api.controller.FazendaController;
import br.com.agricoladigital.smart_farming_api.controller.PlantioController;
import br.com.agricoladigital.smart_farming_api.controller.TalhaoController;
import br.com.agricoladigital.smart_farming_api.controller.UsuarioController;
import br.com.agricoladigital.smart_farming_api.dto.response.PlantioResponse;
import br.com.agricoladigital.smart_farming_api.dto.response.TokenResponse;
import br.com.agricoladigital.smart_farming_api.dto.response.UsuarioResponse;
import br.com.agricoladigital.smart_farming_api.entity.PerfilUsuario;
import br.com.agricoladigital.smart_farming_api.entity.StatusPlantio;
import br.com.agricoladigital.smart_farming_api.entity.Usuario;
import br.com.agricoladigital.smart_farming_api.exception.ApiExceptionHandler;
import br.com.agricoladigital.smart_farming_api.exception.LoginBloqueadoException;
import br.com.agricoladigital.smart_farming_api.repository.FazendaRepository;
import br.com.agricoladigital.smart_farming_api.repository.UsuarioRepository;
import br.com.agricoladigital.smart_farming_api.service.AuthService;
import br.com.agricoladigital.smart_farming_api.service.CulturaService;
import br.com.agricoladigital.smart_farming_api.service.FazendaService;
import br.com.agricoladigital.smart_farming_api.service.PlantioService;
import br.com.agricoladigital.smart_farming_api.service.TalhaoService;
import br.com.agricoladigital.smart_farming_api.service.TokenService;
import br.com.agricoladigital.smart_farming_api.service.UsuarioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {AuthController.class, UsuarioController.class, FazendaController.class, TalhaoController.class,
        CulturaController.class, PlantioController.class})
@Import({
        SecurityConfig.class, CorsConfig.class, JwtConfig.class, TokenService.class, SegurancaErrorHandler.class,
        UsuarioAtivoJwtConverter.class, AcessoService.class, ApiExceptionHandler.class
})
@TestPropertySource(properties = {
        "app.jwt.secret=segredo-de-teste-com-mais-de-32-caracteres!",
        "app.jwt.expiration-ms=900000",
        "app.cors.allowed-origins=http://localhost:3000, https://app.exemplo.com"
})
class SegurancaApiTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private TokenService tokenService;

    @MockitoBean private UsuarioRepository usuarioRepository;
    @MockitoBean private FazendaRepository fazendaRepository;
    @MockitoBean private UsuarioService usuarioService;
    @MockitoBean private FazendaService fazendaService;
    @MockitoBean private AuthService authService;
    @MockitoBean private TalhaoService talhaoService;
    @MockitoBean private CulturaService culturaService;
    @MockitoBean private PlantioService plantioService;

    private Usuario usuario(long id, PerfilUsuario perfil, boolean ativo) {
        Usuario usuario = new Usuario("Usuario " + id, "u" + id + "@example.com", "hash", perfil);
        ReflectionTestUtils.setField(usuario, "id", id);
        if (!ativo) {
            usuario.desativar();
        }
        when(usuarioRepository.findById(id)).thenReturn(Optional.of(usuario));
        return usuario;
    }

    private String bearer(Usuario usuario) {
        return "Bearer " + tokenService.gerarAccessToken(usuario);
    }

    @Test
    void rotaProtegidaSemTokenRetorna401() throws Exception {
        mockMvc.perform(get("/api/v1/usuarios"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void tokenInvalidoRetorna401() throws Exception {
        mockMvc.perform(get("/api/v1/usuarios").header("Authorization", "Bearer lixo"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenDeUsuarioInexistenteRetorna401() throws Exception {
        Usuario fantasma = new Usuario("X", "x@example.com", "hash", PerfilUsuario.ADMINISTRADOR);
        ReflectionTestUtils.setField(fantasma, "id", 77L);
        when(usuarioRepository.findById(77L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/usuarios").header("Authorization", bearer(fantasma)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void usuarioDesativadoRecebe403ComCodigoMesmoComTokenValido() throws Exception {
        Usuario inativo = usuario(1, PerfilUsuario.AGRICULTOR, false);

        mockMvc.perform(get("/api/v1/usuarios/1").header("Authorization", bearer(inativo)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("USUARIO_DESATIVADO"));
    }

    @Test
    void agricultorNaoListaUsuarios() throws Exception {
        Usuario agricultor = usuario(1, PerfilUsuario.AGRICULTOR, true);

        mockMvc.perform(get("/api/v1/usuarios").header("Authorization", bearer(agricultor)))
                .andExpect(status().isForbidden());
    }

    @Test
    void administradorListaUsuarios() throws Exception {
        Usuario admin = usuario(1, PerfilUsuario.ADMINISTRADOR, true);
        when(usuarioService.listar()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/usuarios").header("Authorization", bearer(admin)))
                .andExpect(status().isOk());
    }

    @Test
    void perfilVemDoBancoENaoDoToken() throws Exception {
        Usuario admin = usuario(1, PerfilUsuario.ADMINISTRADOR, true);
        String token = bearer(admin);
        // O usuário foi rebaixado depois da emissão do token.
        ReflectionTestUtils.setField(admin, "perfil", PerfilUsuario.AGRICULTOR);

        mockMvc.perform(get("/api/v1/usuarios").header("Authorization", token))
                .andExpect(status().isForbidden());
    }

    @Test
    void agricultorConsultaApenasOProprioUsuario() throws Exception {
        Usuario agricultor = usuario(1, PerfilUsuario.AGRICULTOR, true);
        when(usuarioService.buscar(1L)).thenReturn(
                new UsuarioResponse(1L, "Usuario 1", "u1@example.com", PerfilUsuario.AGRICULTOR, true));

        mockMvc.perform(get("/api/v1/usuarios/1").header("Authorization", bearer(agricultor)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/usuarios/2").header("Authorization", bearer(agricultor)))
                .andExpect(status().isForbidden());
        verify(usuarioService, never()).buscar(2L);
    }

    @Test
    void agricultorNaoAcessaFazendaDeOutroUsuario() throws Exception {
        Usuario agricultor = usuario(1, PerfilUsuario.AGRICULTOR, true);
        when(fazendaRepository.existsByIdAndUsuario_Id(10L, 1L)).thenReturn(true);
        when(fazendaRepository.existsByIdAndUsuario_Id(20L, 1L)).thenReturn(false);

        mockMvc.perform(get("/api/v1/fazendas/10").header("Authorization", bearer(agricultor)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/fazendas/20").header("Authorization", bearer(agricultor)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/fazendas/20/talhoes").header("Authorization", bearer(agricultor)))
                .andExpect(status().isForbidden());
    }

    @Test
    void agricultorListaApenasAsPropriasFazendas() throws Exception {
        Usuario agricultor = usuario(1, PerfilUsuario.AGRICULTOR, true);
        when(fazendaService.listarPorUsuario(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/fazendas").header("Authorization", bearer(agricultor)))
                .andExpect(status().isOk());

        verify(fazendaService).listarPorUsuario(1L);
        verify(fazendaService, never()).listar();
    }

    @Test
    void agricultorNaoPodeCriarFazendaParaOutroUsuario() throws Exception {
        Usuario agricultor = usuario(1, PerfilUsuario.AGRICULTOR, true);

        mockMvc.perform(post("/api/v1/usuarios/2/fazendas")
                        .header("Authorization", bearer(agricultor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"F\",\"areaTotal\":10}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(fazendaService);
    }

    @Test
    void somenteAdministradorDesativaOutrosUsuarios() throws Exception {
        Usuario agricultor = usuario(1, PerfilUsuario.AGRICULTOR, true);
        Usuario admin = usuario(2, PerfilUsuario.ADMINISTRADOR, true);

        mockMvc.perform(patch("/api/v1/usuarios/3/desativar").header("Authorization", bearer(agricultor)))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/usuarios/3/ativar").header("Authorization", bearer(agricultor)))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/usuarios/3/desativar").header("Authorization", bearer(admin)))
                .andExpect(status().isNoContent());
        verify(usuarioService).desativar(3L, 2L);
    }

    @Test
    void usuarioDesativaAPropriaContaSemInformarId() throws Exception {
        Usuario agricultor = usuario(1, PerfilUsuario.AGRICULTOR, true);

        mockMvc.perform(patch("/api/v1/usuarios/me/desativar").header("Authorization", bearer(agricultor)))
                .andExpect(status().isNoContent());

        verify(usuarioService).desativar(1L, 1L);
    }

    @Test
    void agricultorNaoAlteraSenhaDeOutroUsuario() throws Exception {
        Usuario agricultor = usuario(1, PerfilUsuario.AGRICULTOR, true);

        mockMvc.perform(patch("/api/v1/usuarios/2/senha")
                        .header("Authorization", bearer(agricultor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senhaAtual\":\"abc\",\"novaSenha\":\"12345678\"}"))
                .andExpect(status().isForbidden());
        verify(usuarioService, never()).alterarSenha(anyLong(), any());
    }

    @Test
    void loginERegistroSaoPublicos() throws Exception {
        when(authService.login(any(), any())).thenReturn(new TokenResponse("a", "r", "Bearer", 900));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@example.com\",\"senha\":\"12345678\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("a"));
    }

    @Test
    void loginBloqueadoRetorna429ComRetryAfterECodigo() throws Exception {
        when(authService.login(any(), any())).thenThrow(new LoginBloqueadoException(600));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@example.com\",\"senha\":\"12345678\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "600"))
                .andExpect(jsonPath("$.codigo").value("LOGIN_BLOQUEADO"));
    }

    @Test
    void corsPermiteSomenteOrigensConfiguradas() throws Exception {
        mockMvc.perform(options("/api/v1/fazendas")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));

        mockMvc.perform(options("/api/v1/fazendas")
                        .header("Origin", "https://malicioso.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void respostaDeErroTambemTemCabecalhosCorsParaOrigemPermitida() throws Exception {
        mockMvc.perform(get("/api/v1/fazendas").header("Origin", "https://app.exemplo.com"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://app.exemplo.com"));
    }

    @Test
    void cadastroPublicoIgnoraPerfilEnviadoNoCorpo() throws Exception {
        when(authService.registrar(any())).thenReturn(
                new UsuarioResponse(5L, "Ana", "ana@example.com", PerfilUsuario.AGRICULTOR, true));

        mockMvc.perform(post("/api/v1/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Ana\",\"email\":\"ana@example.com\",\"senha\":\"12345678\","
                                + "\"perfil\":\"ADMINISTRADOR\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.perfil").value("AGRICULTOR"));
    }

    @Test
    void agricultorConsultaCulturasMasSoAdministradorAltera() throws Exception {
        Usuario agricultor = usuario(1, PerfilUsuario.AGRICULTOR, true);
        Usuario admin = usuario(2, PerfilUsuario.ADMINISTRADOR, true);
        String corpo = "{\"nome\":\"Soja\",\"cicloDias\":120}";

        mockMvc.perform(get("/api/v1/culturas").header("Authorization", bearer(agricultor)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/culturas").header("Authorization", bearer(agricultor))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/culturas/1").header("Authorization", bearer(agricultor))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/culturas/1").header("Authorization", bearer(agricultor)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/culturas/1").header("Authorization", bearer(admin)))
                .andExpect(status().isNoContent());
        verify(culturaService, never()).criar(any());
    }

    @Test
    void agricultorSoGerenciaPlantiosDasPropriasFazendas() throws Exception {
        Usuario agricultor = usuario(1, PerfilUsuario.AGRICULTOR, true);
        when(fazendaRepository.existsByIdAndUsuario_Id(10L, 1L)).thenReturn(true);
        when(fazendaRepository.existsByIdAndUsuario_Id(20L, 1L)).thenReturn(false);
        when(plantioService.criar(eq(10L), eq(5L), any())).thenReturn(new PlantioResponse(
                1L, 5L, 1L, "Soja", null, "2025/2026", LocalDate.of(2026, 3, 1), null, null, null, null,
                StatusPlantio.PLANEJADO));
        String corpo = "{\"culturaId\":1,\"dataPlantio\":\"2026-03-01\"}";

        mockMvc.perform(get("/api/v1/fazendas/10/talhoes/5/plantios").header("Authorization", bearer(agricultor)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/fazendas/10/talhoes/5/plantios").header("Authorization", bearer(agricultor))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/fazendas/20/talhoes/5/plantios").header("Authorization", bearer(agricultor))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/fazendas/20/talhoes/5/plantios/1/status")
                        .header("Authorization", bearer(agricultor))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"EM_CULTIVO\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/fazendas/20/talhoes/5/plantios/1/colheita")
                        .header("Authorization", bearer(agricultor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dataColheitaReal\":\"2026-07-01\",\"quantidadeColhidaKg\":100}"))
                .andExpect(status().isForbidden());
        verify(plantioService, never()).alterarStatus(anyLong(), anyLong(), anyLong(), any());
        verify(plantioService, never()).registrarColheita(anyLong(), anyLong(), anyLong(), any());
    }

    @Test
    void safraForaDoFormatoRetorna400() throws Exception {
        Usuario admin = usuario(1, PerfilUsuario.ADMINISTRADOR, true);

        mockMvc.perform(post("/api/v1/fazendas/10/talhoes/5/plantios").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"culturaId\":1,\"safra\":\"2026-27\",\"dataPlantio\":\"2026-03-01\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void statusInvalidoNoFiltroRetorna400() throws Exception {
        Usuario admin = usuario(1, PerfilUsuario.ADMINISTRADOR, true);

        mockMvc.perform(get("/api/v1/fazendas/10/talhoes/5/plantios?status=XYZ")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isBadRequest());
    }
}
