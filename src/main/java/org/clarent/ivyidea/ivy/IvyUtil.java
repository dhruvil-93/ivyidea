/*
 * Copyright 2010 Guy Mahieu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.clarent.ivyidea.ivy;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.apache.ivy.Ivy;
import org.apache.ivy.core.event.EventManager;
import org.apache.ivy.core.module.descriptor.Configuration;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.plugins.parser.ModuleDescriptorParserRegistry;
import org.apache.ivy.plugins.resolver.BasicResolver;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.ivy.plugins.trigger.Trigger;
import org.clarent.ivyidea.intellij.IntellijUtils;
import org.clarent.ivyidea.intellij.facet.config.IvyIdeaFacetConfiguration;
import org.clarent.ivyidea.logging.ConsoleViewMessageLogger;
import org.clarent.ivyidea.util.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;

/**
 * @author Guy Mahieu
 */

public class IvyUtil {

    private static final Logger LOGGER = Logger.getLogger(IvyUtil.class.getName());

    /**
     * Returns the ivy file for the given module.
     *
     * @param module the IntelliJ module for which you want to lookup the ivy file
     * @return the File representing the ivy xml file for the given module
     * @throws RuntimeException if the given module does not have an IvyIDEA facet configured.
     */
    @Nullable
    public static File getIvyFile(Module module) {
        final IvyIdeaFacetConfiguration configuration = IvyIdeaFacetConfiguration.getInstance(module);
        if (configuration == null) {
            throw new RuntimeException("Internal error: No IvyIDEA facet configured for module " + module.getName() + ", but an attempt was made to use it as such.");
        }

        String ivyFile = configuration.getIvyFile();
        if (StringUtils.isBlank(ivyFile)) {
            return null;
        }

        return new File(ivyFile);
    }

    /**
     * Parses the given ivyFile into a ModuleDescriptor using the given settings.
     *
     * @param ivyFile  the ivy file to parse
     * @param ivy the Ivy engine to use, configured with the appropriate settings
     * @return the ModuleDescriptor object representing the ivy file.
     */
    public static ModuleDescriptor parseIvyFile(@NotNull File ivyFile, @NotNull Ivy ivy) {
        LOGGER.info("Parsing ivy file " + ivyFile.getAbsolutePath());

        ModuleDescriptor moduleDescriptor;
        try {
            ivy.pushContext();
            moduleDescriptor = ModuleDescriptorParserRegistry.getInstance().parseDescriptor(ivy.getSettings(), ivyFile.toURI().toURL(), false);
        } catch (ParseException | IOException e) {
            throw new RuntimeException(e);
        } finally {
            ivy.popContext();
        }

        return moduleDescriptor;
    }

    /**
     * Gives a set of configurations defined in the given ivyFileName.
     * Will never throw an exception, if something goes wrong, null is returned
     *
     * @param ivyFileName the name of the ivy file to parse
     * @param ivy the Ivy engine to use, configured with the appropriate settings
     * @return a set of configurations, null if anything went wrong parsing the ivy file
     *
     * @throws java.text.ParseException if there was an error parsing the ivy file; if the file
     *          does not exist or is a directory, no exception will be thrown
     */
    @Nullable
    public static Set<Configuration> loadConfigurations(@NotNull String ivyFileName, @NotNull Ivy ivy) throws ParseException {
        try {
            final File file = new File(ivyFileName);
            if (file.exists() && !file.isDirectory()) {
                final ModuleDescriptor md = parseIvyFile(file, ivy);
                Set<Configuration> result = new TreeSet<>((o1, o2) -> o1.getName().compareToIgnoreCase(o2.getName()));
                result.addAll(Arrays.asList(md.getConfigurations()));
                return result;
            } else {
                return null;
            }
        } catch (RuntimeException e) {
            // Not able to parse module descriptor; no problem here...
            LOGGER.info("Error while parsing ivy file during attempt to load configurations from it: " + e);
            if (e.getCause() instanceof ParseException) {
                throw (ParseException) e.getCause();
            }
            return null;
        }
    }

    public static Ivy createConfiguredIvyEngine(Module module, IvySettings ivySettings) {
        final Ivy ivy = Ivy.newInstance(ivySettings);

        // we should now call the Ivy#postConfigure() method, but it is private :-(
        // so we have to execute the same code ourselves
        postConfigure(ivy);

        registerConsoleLogger(ivy, module.getProject());
        return ivy;
    }

    private static void postConfigure(final Ivy ivy) {
        EventManager eventManager = ivy.getEventManager();
        IvySettings settings = ivy.getSettings();
        List<Trigger> triggers = settings.getTriggers();
        for (Trigger trigger : triggers) {
            eventManager.addIvyListener(trigger, trigger.getEventFilter());
        }

        for (DependencyResolver resolver : settings.getResolvers()) {
            if (resolver instanceof BasicResolver) {
                ((BasicResolver) resolver).setEventManager(eventManager);
            }
        }
    }

    private static void registerConsoleLogger(final Ivy ivy, final Project project) {
        ivy.getLoggerEngine().pushLogger(
                new ConsoleViewMessageLogger(
                        project,
                        IntellijUtils.getConsoleView(project)
                )
        );
    }
}
