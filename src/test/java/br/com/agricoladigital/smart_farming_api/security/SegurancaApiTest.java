package br.com.agricoladigital.smart_farming_api.security;

import br.com.agricoladigital.smart_farming_api.config.CorsConfig;
import br.com.agricoladigital.smart_farming_api.config.JwtConfig;
import br.com.agricoladigital.smart_farming_api.config.SecurityConfig;
import br.com.agricoladigital.smart_farming_api.controller.AuthController;
import br.com.agricoladigital.smart_farming_api.controller.CulturaController;
import br.com.agricoladigital.smart_farming_api.controller.FazendaController;
import br.com.agricoladigital.smart_farming_api.controller.LeituraController;
import br.com.agricoladigital.smart_farming_api.controller.MapaController;
import br.com.agricoladigital.smart_farming_api.controller.ResumoController;
import br.com.agricoladigital.smart_farming_api.controller.EstacaoController;
import br.com.agricoladigital.smart_farming_api.controller.PlantioController;
import br.com.agricoladigital.smart_farming_api.controller.AlertaController;
import br.com.agricoladigital.smart_farming_api.controller.SimuladorController;
import br.com.agricoladigital.smart_farming_api.controller.RegraAlertaController;
import br.com.agricoladigital.smart_farming_api.controller.PrevisaoController;
import br.com.agricoladigital.smart_farming_api.controller.SensorController;
import br.com.agricoladigital.smart_farming_api.controller.TalhaoController;
import br.com.agricoladigital.smart_farming_api.controller.UsuarioController;
import br.com.agricoladigital.smart_farming_api.dto.response.MapaFazendaResponse;
import br.com.agricoladigital.smart_farming_api.dto.response.PlantioResponse;
import br.com.agricoladigital.smart_farming_api.dto.response.ResumoAlertasResponse;
import br.com.agricoladigital.smart_farming_api.dto.response.TokenResponse;
import br.com.agricoladigital.smart_farming_api.dto.response.UsuarioResponse;
import br.com.agricoladigital.smart_farming_api.entity.PerfilUsuario;
import br.com.agricoladigital.smart_farming_api.entity.StatusPlantio;
import br.com.agricoladigital.smart_farming_api.entity.Usuario;
import br.com.agricoladigital.smart_farming_api.exception.ApiExceptionHandler;
import br.com.agricoladigital.smart_farming_api.exception.LoginBloqueadoException;
import br.com.agricoladigital.smart_farming_api.exception.PrevisaoIndisponivelException;
import br.com.agricoladigital.smart_farming_api.repository.FazendaRepository;
import br.com.agricoladigital.smart_farming_api.repository.UsuarioRepository;
import br.com.agricoladigital.smart_farming_api.service.AlertaService;
import br.com.agricoladigital.smart_farming_api.service.CenarioSimulacaoService;
import br.com.agricoladigital.smart_farming_api.service.AuthService;
import br.com.agricoladigital.smart_farming_api.service.CulturaService;
import br.com.agricoladigital.smart_farming_api.service.FazendaService;
import br.com.agricoladigital.smart_farming_api.service.LeituraService;
import br.com.agricoladigital.smart_farming_api.service.MapaService;
import br.com.agricoladigital.smart_farming_api.service.ResumoService;
import br.com.agricoladigital.smart_farming_api.service.EstacaoService;
import br.com.agricoladigital.smart_farming_api.service.PlantioService;
import br.com.agricoladigital.smart_farming_api.service.RegraAlertaService;
import br.com.agricoladigital.smart_farming_api.service.PrevisaoService;
import br.com.agricoladigital.smart_farming_api.service.SensorService;
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
        CulturaController.class, PlantioController.class, SensorController.class, LeituraController.class, MapaController.class, ResumoController.class, PrevisaoController.class, EstacaoController.class, RegraAlertaController.class, AlertaController.class, SimuladorController.class})
@Import({
        SecurityConfig.class, CorsConfig.class, JwtConfig.class, TokenService.class, SegurancaErrorHandler.class,
        UsuarioAtivoJwtConverter.class, AcessoService.class, ApiExceptionHandler.class
})
@TestPropertySource(properties = {
        "app.jwt.secret=segredo-de-teste-com-mais-de-32-caracteres!",
        "app.jwt.expiration-ms=900000",
        "app.simulador.enabled=true",
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
    @MockitoBean private SensorService sensorService;
    @MockitoBean private LeituraService leituraService;
    @MockitoBean private MapaService mapaService;
    @MockitoBean private ResumoService resumoService;
    @MockitoBean private PrevisaoService previsaoService;
    @MockitoBean private EstacaoService estacaoService;
    @MockitoBean private RegraAlertaService regraAlertaService;
    @MockitoBean private AlertaService alertaService;
    @MockitoBean private CenarioSimulacaoService cenarioSimulacaoService;

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
    void agricultorSoGerenciaSensoresELeiturasDasPropriasFazendas() throws Exception {
        Usuario agricultor = usuario(1, PerfilUsuario.AGRICULTOR, true);
        when(fazendaRepository.existsByIdAndUsuario_Id(10L, 1L)).thenReturn(true);
        when(fazendaRepository.existsByIdAndUsuario_Id(20L, 1L)).thenReturn(false);
        String corpo = "{\"codigo\":\"SOLO-01\",\"variavel\":\"UMIDADE_SOLO\"}";

        mockMvc.perform(get("/api/v1/fazendas/10/talhoes/5/sensores").header("Authorization", bearer(agricultor)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/fazendas/10/talhoes/5/leituras/atuais").header("Authorization", bearer(agricultor)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/fazendas/20/talhoes/5/sensores").header("Authorization", bearer(agricultor))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/fazendas/20/talhoes/5/sensores/9/status")
                        .header("Authorization", bearer(agricultor))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INATIVO\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/fazendas/20/talhoes/5/sensores/9").header("Authorization", bearer(agricultor)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/fazendas/20/talhoes/5/sensores/9/leituras").header("Authorization", bearer(agricultor)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/fazendas/20/talhoes/5/leituras/atuais").header("Authorization", bearer(agricultor)))
                .andExpect(status().isForbidden());
        verify(sensorService, never()).criar(anyLong(), anyLong(), any());
        verify(leituraService, never()).historico(anyLong(), anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    void agricultorSoVeOMapaDasPropriasFazendas() throws Exception {
        Usuario agricultor = usuario(1, PerfilUsuario.AGRICULTOR, true);
        when(fazendaRepository.existsByIdAndUsuario_Id(10L, 1L)).thenReturn(true);
        when(fazendaRepository.existsByIdAndUsuario_Id(20L, 1L)).thenReturn(false);
        when(mapaService.mapaDaFazenda(10L)).thenReturn(new MapaFazendaResponse(
                new MapaFazendaResponse.FazendaMapa(10L, "F", null, null, null, null, null, 10.0), List.of(), ResumoAlertasResponse.NENHUM));

        mockMvc.perform(get("/api/v1/fazendas/10/mapa").header("Authorization", bearer(agricultor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fazenda.id").value(10));
        mockMvc.perform(get("/api/v1/fazendas/20/mapa").header("Authorization", bearer(agricultor)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/fazendas/10/mapa"))
                .andExpect(status().isUnauthorized());
        verify(mapaService, never()).mapaDaFazenda(20L);
    }

    @Test
    void resumoGeralUsaAsFazendasDoAgricultorOuTodasParaOAdministrador() throws Exception {
        Usuario agricultor = usuario(1, PerfilUsuario.AGRICULTOR, true);
        Usuario admin = usuario(2, PerfilUsuario.ADMINISTRADOR, true);
        when(resumoService.resumoGeral(any())).thenReturn(null);

        mockMvc.perform(get("/api/v1/resumo").header("Authorization", bearer(agricultor))).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/resumo").header("Authorization", bearer(admin))).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/resumo")).andExpect(status().isUnauthorized());

        verify(resumoService).resumoGeral(1L);
        verify(resumoService).resumoGeral(null);
    }

    @Test
    void agricultorSoVeOResumoDasPropriasFazendas() throws Exception {
        Usuario agricultor = usuario(1, PerfilUsuario.AGRICULTOR, true);
        when(fazendaRepository.existsByIdAndUsuario_Id(10L, 1L)).thenReturn(true);
        when(fazendaRepository.existsByIdAndUsuario_Id(20L, 1L)).thenReturn(false);

        mockMvc.perform(get("/api/v1/fazendas/10/resumo").header("Authorization", bearer(agricultor)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/fazendas/20/resumo").header("Authorization", bearer(agricultor)))
                .andExpect(status().isForbidden());
        verify(resumoService, never()).resumoDaFazenda(20L);
    }

    @Test
    void agricultorSoVeEAtualizaAPrevisaoDasPropriasFazendas() throws Exception {
        Usuario agricultor = usuario(1, PerfilUsuario.AGRICULTOR, true);
        when(fazendaRepository.existsByIdAndUsuario_Id(10L, 1L)).thenReturn(true);
        when(fazendaRepository.existsByIdAndUsuario_Id(20L, 1L)).thenReturn(false);

        mockMvc.perform(get("/api/v1/fazendas/10/previsao").header("Authorization", bearer(agricultor)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/fazendas/10/previsao/atualizar").header("Authorization", bearer(agricultor)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/fazendas/20/previsao").header("Authorization", bearer(agricultor)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/fazendas/20/previsao/atualizar").header("Authorization", bearer(agricultor)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/fazendas/10/previsao")).andExpect(status().isUnauthorized());
        verify(previsaoService, never()).atualizarManualmente(20L);
    }

    @Test
    void agricultorSoGerenciaAEstacaoDasPropriasFazendas() throws Exception {
        Usuario agricultor = usuario(1, PerfilUsuario.AGRICULTOR, true);
        when(fazendaRepository.existsByIdAndUsuario_Id(10L, 1L)).thenReturn(true);
        when(fazendaRepository.existsByIdAndUsuario_Id(20L, 1L)).thenReturn(false);

        mockMvc.perform(get("/api/v1/fazendas/10/estacao").header("Authorization", bearer(agricultor)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/fazendas/10/estacao/leituras?variavel=TEMPERATURA_AR")
                        .header("Authorization", bearer(agricultor)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/fazendas/10/estacao").header("Authorization", bearer(agricultor)))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/v1/fazendas/20/estacao").header("Authorization", bearer(agricultor)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/fazendas/20/estacao").header("Authorization", bearer(agricultor)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/fazendas/20/estacao/sincronizar").header("Authorization", bearer(agricultor)))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/fazendas/20/estacao/status").header("Authorization", bearer(agricultor))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INATIVO\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/fazendas/20/estacao").header("Authorization", bearer(agricultor)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/fazendas/10/estacao")).andExpect(status().isUnauthorized());
        verify(estacaoService, never()).criar(20L);
        verify(estacaoService, never()).remover(20L);
    }

    @Test
    void estacaoExigeVariavelNoHistoricoEStatusValido() throws Exception {
        Usuario admin = usuario(1, PerfilUsuario.ADMINISTRADOR, true);

        mockMvc.perform(get("/api/v1/fazendas/10/estacao/leituras").header("Authorization", bearer(admin)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/v1/fazendas/10/estacao/status").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"EXPLODIDO\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void qualquerUsuarioConsultaAsRegrasDeAlertaMasSoOAdministradorAjusta() throws Exception {
        Usuario agricultor = usuario(1, PerfilUsuario.AGRICULTOR, true);
        Usuario admin = usuario(2, PerfilUsuario.ADMINISTRADOR, true);
        String corpo = "{\"limiar\":25,\"janelaHoras\":3,\"ativa\":true}";

        mockMvc.perform(get("/api/v1/regras-alerta").header("Authorization", bearer(agricultor)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/regras-alerta/1").header("Authorization", bearer(agricultor)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/regras-alerta")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/regras-alerta/1").header("Authorization", bearer(agricultor))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/regras-alerta/1").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isOk());
        verify(regraAlertaService, times(1)).atualizar(eq(1L), any());
    }

    @Test
    void ajusteDeRegraSemLimiarOuComJanelaInvalidaRetorna400() throws Exception {
        Usuario admin = usuario(2, PerfilUsuario.ADMINISTRADOR, true);

        mockMvc.perform(put("/api/v1/regras-alerta/1").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"janelaHoras\":3,\"ativa\":true}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/regras-alerta/1").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"limiar\":25,\"janelaHoras\":500,\"ativa\":true}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/regras-alerta?evento=INUNDACAO").header("Authorization", bearer(admin)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void provedorForaDoArRetorna503SemVazarDetalhes() throws Exception {
        Usuario admin = usuario(1, PerfilUsuario.ADMINISTRADOR, true);
        when(previsaoService.atualizarManualmente(10L))
                .thenThrow(new PrevisaoIndisponivelException("connect timed out api.open-meteo.com"));

        mockMvc.perform(post("/api/v1/fazendas/10/previsao/atualizar").header("Authorization", bearer(admin)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.mensagem").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("open-meteo"))));
    }

    @Test
    void sensorComVariavelDesconhecidaRetorna400() throws Exception {
        Usuario admin = usuario(1, PerfilUsuario.ADMINISTRADOR, true);

        mockMvc.perform(post("/api/v1/fazendas/10/talhoes/5/sensores").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"X\",\"variavel\":\"RADIACAO_GAMA\"}"))
                .andExpect(status().isBadRequest());
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

    @Test
    void alertasSoSaoAcessiveisAoDonoDaFazendaEAoAdministrador() throws Exception {
        Usuario dono = usuario(1, PerfilUsuario.AGRICULTOR, true);
        Usuario admin = usuario(2, PerfilUsuario.ADMINISTRADOR, true);
        when(fazendaRepository.existsByIdAndUsuario_Id(10L, 1L)).thenReturn(true);
        when(fazendaRepository.existsByIdAndUsuario_Id(20L, 1L)).thenReturn(false);
        String corpo = "{\"status\":\"EM_ATENDIMENTO\"}";

        mockMvc.perform(get("/api/v1/fazendas/10/alertas")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/fazendas/10/alertas").header("Authorization", bearer(dono)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/fazendas/10/alertas/5").header("Authorization", bearer(dono)))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/fazendas/10/alertas/5/status").header("Authorization", bearer(dono))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isOk());
        verify(alertaService).alterarStatus(eq(10L), eq(5L), eq(1L), any());

        // Fazenda de outro agricultor: nada chega ao serviço.
        mockMvc.perform(get("/api/v1/fazendas/20/alertas").header("Authorization", bearer(dono)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/fazendas/20/alertas/5").header("Authorization", bearer(dono)))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/fazendas/20/alertas/5/status").header("Authorization", bearer(dono))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isForbidden());
        verify(alertaService, never()).buscar(eq(20L), anyLong());
        verify(alertaService, times(1)).alterarStatus(anyLong(), anyLong(), anyLong(), any());

        mockMvc.perform(get("/api/v1/fazendas/20/alertas").header("Authorization", bearer(admin)))
                .andExpect(status().isOk());
    }

    @Test
    void alteracaoDeStatusDeAlertaExigeStatusValido() throws Exception {
        Usuario admin = usuario(2, PerfilUsuario.ADMINISTRADOR, true);

        mockMvc.perform(patch("/api/v1/fazendas/10/alertas/5/status").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/v1/fazendas/10/alertas/5/status").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"EXPLODIDO\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/fazendas/10/alertas?criticidade=URGENTE").header("Authorization", bearer(admin)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/fazendas/10/alertas?de=ontem").header("Authorization", bearer(admin)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cenariosDoSimuladorSaoSoDoAdministrador() throws Exception {
        Usuario agricultor = usuario(1, PerfilUsuario.AGRICULTOR, true);
        Usuario admin = usuario(2, PerfilUsuario.ADMINISTRADOR, true);
        String corpo = "{\"cenario\":\"SECA\",\"fazendaId\":10}";

        mockMvc.perform(post("/api/v1/simulador/cenario").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/simulador/cenario").header("Authorization", bearer(agricultor))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/simulador/cenario").header("Authorization", bearer(agricultor)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(cenarioSimulacaoService);

        mockMvc.perform(post("/api/v1/simulador/cenario").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/simulador/cenario").header("Authorization", bearer(admin)))
                .andExpect(status().isOk());
        verify(cenarioSimulacaoService).aplicar(any());
    }

    @Test
    void cenarioDoSimuladorExigeCenarioValido() throws Exception {
        Usuario admin = usuario(2, PerfilUsuario.ADMINISTRADOR, true);

        mockMvc.perform(post("/api/v1/simulador/cenario").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/simulador/cenario").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cenario\":\"TERREMOTO\"}"))
                .andExpect(status().isBadRequest());
    }
}
