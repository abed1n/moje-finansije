package me.fit.resource;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import me.fit.dto.CategoryDto;
import me.fit.dto.CategoryRequest;
import me.fit.security.CurrentUser;
import me.fit.service.CategoryService;

import java.util.List;

@Path("/api/categories")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CategoryResource {

    @Inject
    CategoryService categoryService;

    @Inject
    CurrentUser currentUser;

    @GET
    public List<CategoryDto> getCategories() {
        return categoryService.getCategories(currentUser.require());
    }

    @POST
    public Response createCategory(@Valid CategoryRequest request) {
        CategoryDto category = categoryService.createCategory(currentUser.require(), request);
        return Response.status(Response.Status.CREATED).entity(category).build();
    }

    @PUT
    @Path("/{id}")
    public CategoryDto updateCategory(@PathParam("id") Long id, @Valid CategoryRequest request) {
        return categoryService.updateCategory(currentUser.require(), id, request);
    }

    @DELETE
    @Path("/{id}")
    public Response deleteCategory(@PathParam("id") Long id) {
        categoryService.deleteCategory(currentUser.require(), id);
        return Response.noContent().build();
    }
}
