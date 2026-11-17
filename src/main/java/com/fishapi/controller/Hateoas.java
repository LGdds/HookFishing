package com.fishapi.controller;

import org.springframework.data.domain.Page;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.PagedModel;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.function.Function;

/** Monta respostas paginadas (PagedModel) com links de navegação: self, first, prev, next, last. */
final class Hateoas {

    private Hateoas() {
    }

    static <T> PagedModel<EntityModel<T>> paginar(Page<T> page, Function<T, EntityModel<T>> conversor) {
        List<EntityModel<T>> conteudo = page.getContent().stream().map(conversor).toList();

        PagedModel.PageMetadata metadata = new PagedModel.PageMetadata(
                page.getSize(), page.getNumber(), page.getTotalElements(), page.getTotalPages());

        PagedModel<EntityModel<T>> modelo = PagedModel.of(conteudo, metadata);
        modelo.add(Link.of(urlDaPagina(page.getNumber()), IanaLinkRelations.SELF));

        if (page.getTotalPages() > 0) {
            modelo.add(Link.of(urlDaPagina(0), IanaLinkRelations.FIRST));
            modelo.add(Link.of(urlDaPagina(page.getTotalPages() - 1), IanaLinkRelations.LAST));
        }
        if (page.hasPrevious()) {
            modelo.add(Link.of(urlDaPagina(page.getNumber() - 1), IanaLinkRelations.PREV));
        }
        if (page.hasNext()) {
            modelo.add(Link.of(urlDaPagina(page.getNumber() + 1), IanaLinkRelations.NEXT));
        }
        return modelo;
    }

    private static String urlDaPagina(int pagina) {
        return ServletUriComponentsBuilder.fromCurrentRequest()
                .replaceQueryParam("page", pagina)
                .build()
                .toUriString();
    }
}
