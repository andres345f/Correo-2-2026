package com.example.correo.repository;

import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.Optional;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.support.Repositories;
import org.springframework.stereotype.Repository;

/** Acceso generico a las entidades administradas por Spring Data. */
@Repository
public class CrudRepository {
	private final Repositories repositories;
	private final EntityManagerFactory factory;
	public CrudRepository(ApplicationContext context, EntityManagerFactory factory) {
		this.repositories = new Repositories(context);
		this.factory = factory;
	}
	public List<Object> todos(Class<?> tipo) { return repositorio(tipo).findAll(Sort.by("id")); }
	public Optional<Object> buscar(Class<?> tipo, Integer id) { return repositorio(tipo).findById(id); }
	public void guardar(Class<?> tipo, Object entidad) { repositorio(tipo).save(entidad); }
	public void eliminar(Class<?> tipo, Object entidad) { repositorio(tipo).delete(entidad); }
	public void sincronizar(Class<?> tipo) { repositorio(tipo).flush(); }
	public Object identificador(Object entidad) { return factory.getPersistenceUnitUtil().getIdentifier(entidad); }
	@SuppressWarnings("unchecked")
	private JpaRepository<Object, Integer> repositorio(Class<?> tipo) {
		return (JpaRepository<Object, Integer>) repositories.getRepositoryFor(tipo).orElseThrow();
	}
}
