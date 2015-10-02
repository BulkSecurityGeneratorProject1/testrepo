package com.test.app.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import javax.inject.Inject;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.codahale.metrics.annotation.Timed;
import com.test.app.domain.Hospital;
import com.test.app.repository.HospitalRepository;
import com.test.app.web.rest.util.HeaderUtil;
import com.test.app.web.rest.util.PaginationUtil;

/**
 * REST controller for managing HospitalDTO.
 */
@RestController
@RequestMapping("/api")
public class HospitalResource {

    private final Logger log = LoggerFactory.getLogger(HospitalResource.class);

    @Inject
    private HospitalRepository hospitalDTORepository;

    /**
     * POST  /hospitalDTOs -> Create a new hospitalDTO.
     */
    @RequestMapping(value = "/hospitalDTOs",
            method = RequestMethod.POST,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<Hospital> create(@RequestBody Hospital hospitalDTO) throws URISyntaxException {
        log.debug("REST request to save HospitalDTO : {}", hospitalDTO);
        if (hospitalDTO.getId() != null) {
            return ResponseEntity.badRequest().header("Failure", "A new hospitalDTO cannot already have an ID").body(null);
        }
        Hospital result = hospitalDTORepository.save(hospitalDTO);
        return ResponseEntity.created(new URI("/api/hospitalDTOs/" + result.getId()))
                .headers(HeaderUtil.createEntityCreationAlert("hospitalDTO", result.getId().toString()))
                .body(result);
    }

    /**
     * PUT  /hospitalDTOs -> Updates an existing hospitalDTO.
     */
    @RequestMapping(value = "/hospitalDTOs",
        method = RequestMethod.PUT,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<Hospital> update(@RequestBody Hospital hospitalDTO) throws URISyntaxException {
        log.debug("REST request to update HospitalDTO : {}", hospitalDTO);
        if (hospitalDTO.getId() == null) {
            return create(hospitalDTO);
        }
        Hospital result = hospitalDTORepository.save(hospitalDTO);
        return ResponseEntity.ok()
                .headers(HeaderUtil.createEntityUpdateAlert("hospitalDTO", hospitalDTO.getId().toString()))
                .body(result);
    }

    /**
     * GET  /hospitalDTOs -> get all the hospitalDTOs.
     */
    @RequestMapping(value = "/hospitalDTOs",
            method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<List<Hospital>> getAll(@RequestParam(value = "page" , required = false) Integer offset,
                                  @RequestParam(value = "per_page", required = false) Integer limit)
        throws URISyntaxException {
        Page<Hospital> page = hospitalDTORepository.findAll(PaginationUtil.generatePageRequest(offset, limit));
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(page, "/api/hospitalDTOs", offset, limit);
        return new ResponseEntity<List<Hospital>>(page.getContent(), headers, HttpStatus.OK);
    }

    /**
     * GET  /hospitalDTOs/:id -> get the "id" hospitalDTO.
     */
    @RequestMapping(value = "/hospitalDTOs/{id}",
            method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<Hospital> get(@PathVariable String id, HttpServletResponse response) {
        log.debug("REST request to get HospitalDTO : {}", id);
        Hospital hospitalDTO = hospitalDTORepository.findOne(id);
        if (hospitalDTO == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(hospitalDTO, HttpStatus.OK);
    }

    /**
     * DELETE  /hospitalDTOs/:id -> delete the "id" hospitalDTO.
     */
    @RequestMapping(value = "/hospitalDTOs/{id}",
            method = RequestMethod.DELETE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<Void> delete(@PathVariable String id) {
        log.debug("REST request to delete HospitalDTO : {}", id);
        hospitalDTORepository.delete(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert("hospitalDTO", id.toString())).build();
    }
}
