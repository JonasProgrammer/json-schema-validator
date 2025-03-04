/*
 * Copyright (c) 2016 Network New Technologies Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.networknt.schema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AllOfValidator extends BaseJsonValidator implements JsonValidator {
    private static final Logger logger = LoggerFactory.getLogger(AllOfValidator.class);

    private final ValidationContext validationContext;
    private final List<JsonSchema> schemas = new ArrayList<JsonSchema>();

    public AllOfValidator(String schemaPath, JsonNode schemaNode, JsonSchema parentSchema, ValidationContext validationContext) {
        super(schemaPath, schemaNode, parentSchema, ValidatorTypeCode.ALL_OF, validationContext);
        this.validationContext = validationContext;
        int size = schemaNode.size();
        for (int i = 0; i < size; i++) {
            schemas.add(new JsonSchema(validationContext,
                                       getValidatorType().getValue(),
                                       parentSchema.getCurrentUri(),
                                       schemaNode.get(i),
                                       parentSchema));
        }
    }

    public Set<ValidationMessage> validate(JsonNode node, JsonNode rootNode, String at) {
        debug(logger, node, rootNode, at);

        Set<ValidationMessage> errors = new LinkedHashSet<ValidationMessage>();

        for (JsonSchema schema : schemas) {
            errors.addAll(schema.validate(node, rootNode, at));

            if (config.isOpenAPI3StyleDiscriminators()) {
                final Iterator<JsonNode> arrayElements = schemaNode.elements();
                while (arrayElements.hasNext()) {
                    final ObjectNode allOfEntry = (ObjectNode) arrayElements.next();
                    final JsonNode $ref = allOfEntry.get("$ref");
                    if (null != $ref) {
                        final ValidationContext.DiscriminatorContext currentDiscriminatorContext = validationContext
                                .getCurrentDiscriminatorContext();
                        final ObjectNode discriminator = currentDiscriminatorContext
                                .getDiscriminatorForPath(allOfEntry.get("$ref").asText());
                        if (null != discriminator) {
                            registerAndMergeDiscriminator(currentDiscriminatorContext, discriminator, parentSchema, at);
                            // now we have to check whether we have hit the right target
                            final String discriminatorPropertyName = discriminator.get("propertyName").asText();
                            final String discriminatorPropertyValue = node.get(discriminatorPropertyName).textValue();

                            final JsonSchema jsonSchema = parentSchema;
                            checkDiscriminatorMatch(
                                    currentDiscriminatorContext,
                                    discriminator,
                                    discriminatorPropertyValue,
                                    jsonSchema);
                        }
                    }
                }
            }
        }

        return Collections.unmodifiableSet(errors);
    }



    @Override
    public Set<ValidationMessage> walk(JsonNode node, JsonNode rootNode, String at, boolean shouldValidateSchema) {
        Set<ValidationMessage> validationMessages = new LinkedHashSet<ValidationMessage>();

        for (JsonSchema schema : schemas) {
            // Walk through the schema
            validationMessages.addAll(schema.walk(node, rootNode, at, shouldValidateSchema));
        }
        return Collections.unmodifiableSet(validationMessages);
    }

}
