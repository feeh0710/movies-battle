package br.com.fernando.moviesbattle.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import br.com.fernando.moviesbattle.domain.Filme;
import br.com.fernando.moviesbattle.dto.Login;
import br.com.fernando.moviesbattle.dto.Resposta;
import br.com.fernando.moviesbattle.model.Partida;
import br.com.fernando.moviesbattle.model.Ranking;
import br.com.fernando.moviesbattle.model.Sessao;
import br.com.fernando.moviesbattle.model.Usuario;
import br.com.fernando.moviesbattle.service.FilmeService;
import br.com.fernando.moviesbattle.service.PartidaService;
import br.com.fernando.moviesbattle.service.SessaoService;
import br.com.fernando.moviesbattle.service.UsuarioService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

@Api(value = "Partida")
@RestController
@RequestMapping("api/partida")
public class PartidaResource {
	@Autowired
	PartidaService partService;

	@Autowired
	UsuarioService usuarioService;

	@Autowired
	FilmeService filmeService;

	@Autowired
	SessaoService sessaoService;

	@ApiOperation(value = "Inicia uma nova partida")
	@ApiResponses(value = { @ApiResponse(code = 200, message = "Partida Iniciada ou Partida ativa"),
			@ApiResponse(code = 404, message = "Usuario não encontrado") })
	@PostMapping("/novo")
	@ResponseBody
	public ResponseEntity<Partida> iniciar(@RequestBody Login login) {
		Usuario usuario = usuarioService.login(login.nick, login.senha);
		if (usuario != null) {
			if (usuario.getPartida() != null) {
				if (!usuario.getPartida().isAtivo() && usuario.getPartida().getSessao() == null) {
					usuario.getPartida().setAtivo(true);
					usuario.getPartida().setPontos(0);
					usuario.getPartida().setVidas(3);
					usuario.setRanking((usuario.getRanking() != null) ? usuario.getRanking() : new Ranking(0, 0, 0.0));
					usuarioService.create(usuario);
					return ResponseEntity.ok().eTag("Partida iniciada ")
							.body(usuario.getPartida());
				}
				return ResponseEntity.ok().eTag("Partida já está ativa " + usuario.getPartida().getVidas())
						.body(usuario.getPartida());
			} else {
				usuario.setPartida(new Partida(3, true, 0));
				usuarioService.create(usuario);
				return ResponseEntity.ok().eTag("Partida iniciada " + usuario.getPartida().getVidas())
						.body(usuario.getPartida());
			}
		}
		return ResponseEntity.notFound().eTag("Usuário não encontrado.").build();
	}

	@ApiOperation(value = "Inicia uma nova rodada com um par de Filmes")
	@ApiResponses(value = { @ApiResponse(code = 200, message = "Rodada Iniciada"),
			@ApiResponse(code = 404, message = "Erro ao iniciar rodada") })
	@PostMapping("/rodada")
	@ResponseBody
	public ResponseEntity<Filme[]> rodada(@RequestBody Login login) {
		Usuario usuario = usuarioService.login(login.nick, login.senha);
		Filme[] filmes;
		if (usuario != null) {
			if (usuario.getPartida() != null) {
				if (usuario.getPartida().isAtivo()) {

					if (usuario.getPartida().getSessao() != null) {
						filmes = filmeService.getFilmes(usuario.getPartida().getSessao());
						usuario.getPartida().getSessao().setImdbIdFilme1(filmes[0].getImdbID());
						usuario.getPartida().getSessao().setImdbIdFilme2(filmes[1].getImdbID());
						usuario.getPartida().getSessao().setRespondido(false);
						usuario.getPartida().getSessao().setPoster1(filmes[0].getPoster());
						usuario.getPartida().getSessao().setPoster2(filmes[1].getPoster());
						usuarioService.create(usuario);
						return ResponseEntity.ok().eTag("Partida iniciada" + usuario.getPartida().getVidas())
								.body(filmes);
					} else {
						filmes = filmeService.getFilmes(null);
						usuario.getPartida().setSessao(new Sessao(filmes[0].getImdbID(), filmes[1].getImdbID(), false,filmes[0].getPoster(),filmes[1].getPoster()));
						usuarioService.create(usuario);
						return ResponseEntity.ok().eTag("Partida iniciada" + usuario.getPartida().getVidas())
								.body(filmes);
					}
				} else {
					return ResponseEntity.notFound().eTag("Partida não iniciada.").build();
				}
			} else {
				return ResponseEntity.notFound().eTag("Partida não iniciada.").build();
			}

		}
		return ResponseEntity.notFound().eTag("Usuário não encontrado.").build();
	}

	@ApiOperation(value = "Recebe a resposta(true -> Filme 1 | false -> Filme 2) e devolve uma mensagem")
	@ApiResponses(value = { @ApiResponse(code = 200, message = "Resposta correta ou incorreta"),
			@ApiResponse(code = 404, message = "Erro ao iniciar rodada") })
	@PostMapping("/resposta")
	@ResponseBody
	public ResponseEntity<String> resposta(@RequestBody Resposta resposta) {
		Usuario usuario = usuarioService.login(resposta.login.nick, resposta.login.senha);
		if (usuario != null) {
			if (usuario.getPartida() != null) {
				if (usuario.getPartida().getSessao() != null) {
					switch (filmeService.verificaRatingImdbFilme(usuario, resposta)) {
						case "1": // acertou

							usuario.getRanking().setSequenciaQuiz(usuario.getRanking().getSequenciaQuiz() + 1);
							usuario.getPartida().setPontos(usuario.getPartida().getPontos() + 1);

							System.err.println(usuario.getPartida().getSessao().toString());
							usuario.getPartida().setSessao(null);
							usuarioService.create(usuario);
							return ResponseEntity.ok()
									.eTag("RESPOSTA CORRETA | pontos: " + usuario.getPartida().getPontos())
									.body("RESPOSTA CORRETA");

						case "2": // errou
							if (usuario.getPartida().getVidas() > 0) {
								usuario.getRanking().setSequenciaQuiz(usuario.getRanking().getSequenciaQuiz() + 1);
								usuario.getPartida().setVidas(usuario.getPartida().getVidas() - 1);
								sessaoService.deleteById(usuario.getPartida().getSessao().getId());
								usuario.getPartida().setSessao(null);
								usuarioService.create(usuario);

							} else {
								// encerrar partida
								Double pontuacao = partService.calcularPontuacao(usuario);
								usuario.setPartida(null);
								usuarioService.create(usuario);
								return ResponseEntity.ok().eTag("Partida encerrada | seus pontos foram: " + pontuacao)
										.build();
							}

							return ResponseEntity.ok()
									.eTag("RESPOSTA INCORRETA | vidas restantes: " + usuario.getPartida().getVidas())
									.body("RESPOSTA INCORRETA");
						case "3": // null
							System.err.println("case 3");
							return ResponseEntity.notFound().eTag("ERRO DESCONHECIDO.").build();
						default:
							return ResponseEntity.notFound().eTag("Partida não iniciada.").build();
					}
				} else {
					return ResponseEntity.notFound().eTag("Rodada não iniciada.").build();
				}
			}
			System.err.println("usuario.getPartida() != null");
			return ResponseEntity.notFound().eTag("ERRO DESCONHECIDO.").build();
		} else {
			return ResponseEntity.notFound().eTag("Usuário não encontrado.").build();
		}
	}

	@ApiOperation(value = "Encerra a partida")
	@ApiResponses(value = { @ApiResponse(code = 200, message = "Partida encerrada"),
			@ApiResponse(code = 404, message = "Erro ao encerrar partida") })
	@PostMapping("/encerrar")
	@ResponseBody
	public ResponseEntity<Partida> encerrar(@RequestBody Login login) {
		Usuario usuario = usuarioService.login(login.nick, login.senha);
		if (usuario != null) {
			if (usuario.getPartida() != null) {
				if (usuario.getPartida().isAtivo()) {
					if (usuario.getPartida().getVidas() < 3 || usuario.getPartida().getPontos() > 0) {
						Double pontuacao = partService.calcularPontuacao(usuario);
						usuario.setPartida(null);
						usuarioService.create(usuario);
						return ResponseEntity.ok().eTag("Partida encerrada | seus pontos foram: " + pontuacao).build();
					}
					usuario.setPartida(null);
					usuarioService.create(usuario);
					return ResponseEntity.ok().eTag("Partida encerrada").build();
				}
				return ResponseEntity.badRequest().eTag("Partida já está encerrada ").body(usuario.getPartida());
			}
			return ResponseEntity.badRequest().eTag("Partida já esta encerrada").body(usuario.getPartida());
		}
		return ResponseEntity.notFound().eTag("Usuário não encontrado.").build();
	}
}
