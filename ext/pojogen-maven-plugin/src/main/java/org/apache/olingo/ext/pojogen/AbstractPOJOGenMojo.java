/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.olingo.ext.pojogen;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.olingo.client.api.CommonODataClient;
import org.apache.olingo.commons.api.edm.Edm;
import org.apache.olingo.commons.api.edm.EdmComplexType;
import org.apache.olingo.commons.api.edm.EdmEntityContainer;
import org.apache.olingo.commons.api.edm.EdmEntitySet;
import org.apache.olingo.commons.api.edm.EdmEntityType;
import org.apache.olingo.commons.api.edm.EdmEnumType;
import org.apache.olingo.commons.api.edm.EdmSchema;
import org.apache.olingo.commons.api.edm.EdmSingleton;
import org.apache.olingo.commons.api.edm.constants.ODataServiceVersion;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.codehaus.plexus.util.FileUtils;

public abstract class AbstractPOJOGenMojo extends AbstractMojo {

  /**
   * Generated files base root.
   */
  @Parameter(property = "outputDirectory", required = true)
  protected String outputDirectory;

  /**
   * OData service root URL.
   */
  @Parameter(property = "serviceRootURL", required = false)
  protected String serviceRootURL;

  /**
   * Local file from which Edm information can be loaded.
   */
  @Parameter(property = "localEdm", required = false)
  protected String localEdm;

  /**
   * Base package.
   */
  @Parameter(property = "basePackage", required = true)
  protected String basePackage;

  protected final Set<String> namespaces = new HashSet<String>();

  protected static String TOOL_DIR = "ojc-plugin";

  protected AbstractUtility utility;

  protected abstract String getVersion();

  protected File mkPkgDir(final String path) {
    final File dir = new File(outputDirectory + File.separator + TOOL_DIR + File.separator
            + basePackage.replace('.', File.separatorChar) + File.separator + path);

    if (dir.exists()) {
      if (!dir.isDirectory()) {
        throw new IllegalArgumentException("Invalid path '" + path + "': it is not a directory");
      }
    } else {
      dir.mkdirs();
    }

    return dir;
  }

  protected void writeFile(final String name, final File path, final VelocityContext ctx, final Template template,
          final boolean append) throws MojoExecutionException {

    if (!path.exists()) {
      throw new IllegalArgumentException("Invalid base path '" + path.getAbsolutePath() + "'");
    }

    FileWriter writer = null;
    try {
      final File toBeWritten = new File(path, name);
      if (!append && toBeWritten.exists()) {
        throw new IllegalStateException("File '" + toBeWritten.getAbsolutePath() + "' already exists");
      }
      writer = new FileWriter(toBeWritten, append);
      template.merge(ctx, writer);
    } catch (IOException e) {
      throw new MojoExecutionException("Error creating file '" + name + "'", e);
    } finally {
      IOUtils.closeQuietly(writer);
    }
  }

  protected VelocityContext newContext() {
    final VelocityContext ctx = new VelocityContext();

    ctx.put("utility", getUtility());
    ctx.put("basePackage", basePackage);
    ctx.put("schemaName", getUtility().getSchemaName());
    ctx.put("namespace", getUtility().getNamespace());
    ctx.put("namespaces", namespaces);
    ctx.put("odataVersion", getVersion());

    return ctx;
  }

  protected void parseObj(final File base, final String pkg, final String name, final String out)
          throws MojoExecutionException {

    parseObj(base, false, pkg, name, out, Collections.<String, Object>emptyMap());
  }

  protected void parseObj(
          final File base,
          final String pkg,
          final String name,
          final String out,
          final Map<String, Object> objs)
          throws MojoExecutionException {

    parseObj(base, false, pkg, name, out, objs);
  }

  protected void parseObj(
          final File base,
          final boolean append,
          final String pkg,
          final String name,
          final String out,
          final Map<String, Object> objs)
          throws MojoExecutionException {

    final VelocityContext ctx = newContext();
    ctx.put("package", pkg);

    if (objs != null) {
      for (Map.Entry<String, Object> obj : objs.entrySet()) {
        if (StringUtils.isNotBlank(obj.getKey()) && obj.getValue() != null) {
          ctx.put(obj.getKey(), obj.getValue());
        }
      }
    }

    final Template template = Velocity.getTemplate(name + ".vm");
    writeFile(out, base, ctx, template, append);
  }

  protected abstract void createUtility(Edm edm, EdmSchema schema, String basePackage);

  protected abstract AbstractUtility getUtility();

  protected abstract CommonODataClient<?> getClient();

  private Edm getEdm() throws FileNotFoundException {
    if (StringUtils.isEmpty(serviceRootURL) && StringUtils.isEmpty(localEdm)) {
      throw new IllegalArgumentException("Must provide either serviceRootURL or localEdm");
    }
    if (StringUtils.isNotEmpty(serviceRootURL) && StringUtils.isNotEmpty(localEdm)) {
      throw new IllegalArgumentException("Must provide either serviceRootURL or localEdm, not both");
    }

    Edm edm = null;
    if (StringUtils.isNotEmpty(serviceRootURL)) {
      edm = getClient().getRetrieveRequestFactory().getMetadataRequest(serviceRootURL).execute().getBody();
    } else if (StringUtils.isNotEmpty(localEdm)) {
      final FileInputStream fis = new FileInputStream(FileUtils.getFile(localEdm));
      try {
        edm = getClient().getReader().readMetadata(fis);
      } finally {
        IOUtils.closeQuietly(fis);
      }
    }

    if (edm == null) {
      throw new IllegalStateException("Metadata not found");
    }
    return edm;
  }

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    if (new File(outputDirectory + File.separator + TOOL_DIR).exists()) {
      getLog().info("Nothing to do because " + TOOL_DIR + " directory already exists. Clean to update.");
      return;
    }

    Velocity.addProperty(Velocity.RESOURCE_LOADER, "class");
    Velocity.addProperty("class.resource.loader.class", ClasspathResourceLoader.class.getName());

    try {
      final Edm edm = getEdm();

      for (EdmSchema schema : edm.getSchemas()) {
        namespaces.add(schema.getNamespace().toLowerCase());
      }

      for (EdmSchema schema : edm.getSchemas()) {
        createUtility(edm, schema, basePackage);

        // write package-info for the base package
        final String schemaPath = utility.getNamespace().toLowerCase().replace('.', File.separatorChar);
        final File base = mkPkgDir(schemaPath);
        final String pkg = basePackage + "." + utility.getNamespace().toLowerCase();
        parseObj(base, pkg, "package-info", "package-info.java");

        // write package-info for types package
        final File typesBaseDir = mkPkgDir(schemaPath + "/types");
        final String typesPkg = pkg + ".types";
        parseObj(typesBaseDir, typesPkg, "package-info", "package-info.java");

        final Map<String, Object> objs = new HashMap<String, Object>();

        // write types into types package
        for (EdmEnumType enumType : schema.getEnumTypes()) {
          final String className = utility.capitalize(enumType.getName());
          objs.clear();
          objs.put("enumType", enumType);
          parseObj(typesBaseDir, typesPkg, "enumType", className + ".java", objs);
        }

        final List<EdmComplexType> complexes = new ArrayList<EdmComplexType>();
        
        for (EdmComplexType complex : schema.getComplexTypes()) {
          complexes.add(complex);
          final String className = utility.capitalize(complex.getName());
          objs.clear();
          objs.put("complexType", complex);
          parseObj(typesBaseDir, typesPkg, "complexType", className + ".java", objs);
        }

        for (EdmEntityType entity : schema.getEntityTypes()) {
          objs.clear();
          objs.put("entityType", entity);

          final Map<String, String> keys;

          EdmEntityType baseType = null;
          if (entity.getBaseType() == null) {
            keys = getUtility().getEntityKeyType(entity);
          } else {
            baseType = entity.getBaseType();
            objs.put("baseType", getUtility().getJavaType(baseType.getFullQualifiedName().toString()));
            while (baseType.getBaseType() != null) {
              baseType = baseType.getBaseType();
            }
            keys = getUtility().getEntityKeyType(baseType);
          }

          if (keys.size() > 1) {
            // create compound key class
            final String keyClassName = utility.capitalize(baseType == null
                    ? entity.getName()
                    : baseType.getName()) + "Key";
            objs.put("keyRef", keyClassName);

            if (entity.getBaseType() == null) {
              objs.put("keys", keys);
              parseObj(typesBaseDir, typesPkg, "entityTypeKey", keyClassName + ".java", objs);
            }
          }

          parseObj(typesBaseDir, typesPkg, "entityType",
                  utility.capitalize(entity.getName()) + ".java", objs);
          parseObj(typesBaseDir, typesPkg, "entityCollection",
                  utility.capitalize(entity.getName()) + "Collection.java", objs);
        }

        // write container and top entity sets into the base package
        for (EdmEntityContainer container : schema.getEntityContainers()) {
          objs.clear();
          objs.put("container", container);
          objs.put("namespace", schema.getNamespace());
          objs.put("complexes", complexes);

          parseObj(base, pkg, "container",
                  utility.capitalize(container.getName()) + ".java", objs);

          for (EdmEntitySet entitySet : container.getEntitySets()) {
            objs.clear();
            objs.put("entitySet", entitySet);
            parseObj(base, pkg, "entitySet",
                    utility.capitalize(entitySet.getName()) + ".java", objs);
          }

          if (ODataServiceVersion.valueOf(getVersion().toUpperCase()).compareTo(ODataServiceVersion.V40) >= 0) {
            for (EdmSingleton singleton : container.getSingletons()) {
              objs.clear();
              objs.put("singleton", singleton);
              parseObj(base, pkg, "singleton",
                      utility.capitalize(singleton.getName()) + ".java", objs);
            }
          }
        }
      }
    } catch (Exception t) {
      getLog().error(t);

      throw (t instanceof MojoExecutionException)
              ? (MojoExecutionException) t
              : new MojoExecutionException("While executin mojo", t);
    }
  }
}
