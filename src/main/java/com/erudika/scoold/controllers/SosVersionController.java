/*
 * Platforma SOS – platnosť obsahu znalostnej bázy (otázky a odpovede) a verzie otázok.
 *
 * POST /question/{id}/validity     – nastaví platnosť od–do otázky alebo odpovede (parameter postid)
 * POST /question/{id}/new-version  – vytvorí novú verziu otázky platnú od zadaného dňa; pôvodná verzia
 *                                    dostane platnosť do predchádzajúceho dňa a obe sa navzájom prepoja
 *
 * Oprávnenie: len moderátori a správcovia (ScooldUtils.isMod – správca je aj moderátor).
 * Obe verzie sú bežné otázky, preto ich nájde aj vyhľadávanie (s označením platnosti).
 */
package com.erudika.scoold.controllers;

import com.erudika.para.client.ParaClient;
import com.erudika.scoold.core.Post;
import com.erudika.scoold.core.Profile;
import com.erudika.scoold.core.Question;
import com.erudika.scoold.utils.ScooldUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.ArrayList;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/question")
public class SosVersionController {

	private static final Logger logger = LoggerFactory.getLogger(SosVersionController.class);

	private final ScooldUtils utils;
	private final ParaClient pc;

	public SosVersionController(ScooldUtils utils) {
		this.utils = utils;
		this.pc = utils.getParaClient();
	}

	/**
	 * Nastaví platnosť od–do otázky alebo jej odpovede.
	 * @param id id otázky (stránka, na ktorú sa vrátime)
	 * @param postid id upravovaného príspevku (otázka alebo odpoveď), predvolene otázka
	 * @param validFrom platnosť od (yyyy-MM-dd) alebo prázdne
	 * @param validTo platnosť do vrátane (yyyy-MM-dd) alebo prázdne
	 * @param req request
	 * @return presmerovanie späť na otázku
	 */
	@PostMapping("/{id}/validity")
	public String validity(@PathVariable String id, @RequestParam(required = false) String postid,
			@RequestParam(required = false) String validFrom, @RequestParam(required = false) String validTo,
			HttpServletRequest req) {
		Profile authUser = utils.getAuthUser(req);
		Post question = pc.read(id);
		if (question == null) {
			return "redirect:" + ScooldUtils.getConfig().serverContextPath() + "/questions";
		}
		String back = question.getPostLinkForRedirect();
		if (!utils.isMod(authUser)) {
			return "redirect:" + back;
		}
		Post post = StringUtils.isBlank(postid) || id.equals(postid) ? question : pc.read(postid);
		if (post == null || (post != question && !id.equals(post.getParentid()))) {
			return "redirect:" + back;
		}
		LocalDate from = Post.parseDay(validFrom);
		LocalDate to = Post.parseDay(validTo);
		if ((StringUtils.isNotBlank(validFrom) && from == null) || (StringUtils.isNotBlank(validTo) && to == null)
				|| (from != null && to != null && to.isBefore(from))) {
			return "redirect:" + back + "?validityError=true#" + post.getId();
		}
		post.setValidFrom(from == null ? null : from.toString());
		post.setValidTo(to == null ? null : to.toString());
		post.update();
		logger.info("Validity of post {} set to {} – {} by {}.", post.getId(), from, to, authUser.getId());
		utils.triggerHookEvent("question.update", post, req);
		return "redirect:" + back + "#" + post.getId();
	}

	/**
	 * Vytvorí novú verziu otázky platnú od zadaného dňa.
	 * @param id id aktuálnej verzie otázky
	 * @param validFrom platnosť novej verzie od (yyyy-MM-dd), povinné
	 * @param req request
	 * @return presmerovanie na novú verziu (na úpravu obsahu)
	 */
	@PostMapping("/{id}/new-version")
	public String newVersion(@PathVariable String id, @RequestParam(required = false) String validFrom,
			HttpServletRequest req) {
		Profile authUser = utils.getAuthUser(req);
		Post current = pc.read(id);
		if (current == null) {
			return "redirect:" + ScooldUtils.getConfig().serverContextPath() + "/questions";
		}
		String back = current.getPostLinkForRedirect();
		if (!utils.isMod(authUser) || !current.isQuestion()) {
			return "redirect:" + back;
		}
		if (!StringUtils.isBlank(current.getNextVersionId())) {
			// novšia verzia už existuje – pokračujeme od nej (žiadne vetvenie verzií)
			Post next = pc.read(current.getNextVersionId());
			if (next != null) {
				return "redirect:" + next.getPostLinkForRedirect() + "?versionError=exists";
			}
		}
		LocalDate from = Post.parseDay(validFrom);
		LocalDate currentFrom = Post.parseDay(current.getValidFrom());
		if (from == null || (currentFrom != null && !from.isAfter(currentFrom))) {
			return "redirect:" + back + "?versionError=date";
		}

		Question next = new Question();
		next.setTitle(current.getTitle());
		next.setBody(current.getBody());
		next.setTags(current.getTags() == null ? new ArrayList<>() : new ArrayList<>(current.getTags()));
		next.setSpace(current.getSpace());
		next.setCreatorid(authUser.getId());
		next.setAuthor(authUser);
		next.setValidFrom(from.toString());
		LocalDate currentTo = Post.parseDay(current.getValidTo());
		// ak mala pôvodná verzia koniec platnosti v budúcnosti, prevezme ho nová verzia
		next.setValidTo(currentTo != null && !currentTo.isBefore(from) ? currentTo.toString() : null);
		next.setPreviousVersionId(current.getId());
		if (next.create() == null) {
			return "redirect:" + back + "?versionError=create";
		}

		current.setValidTo(from.minusDays(1).toString());
		current.setNextVersionId(next.getId());
		current.update();
		logger.info("New version {} of question {} valid from {} created by {}.", next.getId(), current.getId(),
				from, authUser.getId());
		utils.triggerHookEvent("question.create", next, req);
		return "redirect:" + next.getPostLinkForRedirect() + "?newVersion=true";
	}
}
