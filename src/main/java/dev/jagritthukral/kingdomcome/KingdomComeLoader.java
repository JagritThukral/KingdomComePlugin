package dev.jagritthukral.kingdomcome;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.jetbrains.annotations.NotNull;

public class KingdomComeLoader implements PluginLoader {

    @Override
    public void classloader(@NotNull PluginClasspathBuilder classpathBuilder) {
        MavenLibraryResolver resolver = new MavenLibraryResolver();

        resolver.addRepository(new RemoteRepository.Builder(
                "central", "default", "https://apache.org"
        ).build());

        resolver.addDependency(new Dependency(
                new DefaultArtifact("com.zaxxer:HikariCP:7.1.0"), null
        ));
        resolver.addDependency(new Dependency(new DefaultArtifact("org.jctools:jctools-core:4.0.7"), null));

        classpathBuilder.addLibrary(resolver);
    }
}
