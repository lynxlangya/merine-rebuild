package com.merine.rebuild.agent.semantics.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 业务内容的唯一结构：固定文件与只读接口共用，不包含数据库映射或执行能力。 */
@Schema(name = "SemanticCatalog")
public record SemanticCatalogView(
        @Schema(requiredMode = REQUIRED) @Min(1) int schemaVersion,
        @Schema(requiredMode = REQUIRED) @Min(1) int revision,
        @Schema(requiredMode = REQUIRED) @NotNull @Size(min = 1) List<@NotNull @Valid Topic> topics) {

    public SemanticCatalogView { topics = List.copyOf(topics); }

    @Schema(name = "SemanticTopic")
    public record Topic(
            @Schema(requiredMode = REQUIRED) @NotBlank @Pattern(regexp = "[a-z][a-z0-9.-]*") String id,
            @Schema(requiredMode = REQUIRED) @NotBlank String title,
            @Schema(requiredMode = REQUIRED) @NotBlank String description,
            @Schema(requiredMode = REQUIRED) @NotNull List<@NotNull @Valid Concept> concepts,
            @Schema(requiredMode = REQUIRED) @NotNull List<@NotNull @Valid Metric> metrics) {
        public Topic {
            concepts = List.copyOf(concepts);
            metrics = List.copyOf(metrics);
        }
    }

    @Schema(name = "BusinessConcept")
    public record Concept(
            @Schema(requiredMode = REQUIRED) @NotBlank @Pattern(regexp = "[a-z][a-z0-9.-]*") String id,
            @Schema(requiredMode = REQUIRED) @Min(1) int revision,
            @Schema(requiredMode = REQUIRED) @NotBlank String title,
            @Schema(requiredMode = REQUIRED) @NotBlank String definition,
            @Schema(requiredMode = REQUIRED) @NotNull @Size(min = 1) List<@NotBlank String> rules,
            @Schema(requiredMode = REQUIRED) @NotBlank String example) {
        public Concept { rules = List.copyOf(rules); }
    }

    @Schema(name = "MetricDefinition")
    public record Metric(
            @Schema(requiredMode = REQUIRED) @NotBlank @Pattern(regexp = "[a-z][a-z0-9.-]*") String id,
            @Schema(requiredMode = REQUIRED) @Min(1) int revision,
            @Schema(requiredMode = REQUIRED) @NotBlank String title,
            @Schema(requiredMode = REQUIRED) @NotBlank String definition,
            @Schema(requiredMode = REQUIRED) @NotNull DefinitionStatus status,
            @Schema(requiredMode = REQUIRED) @NotBlank String statusLabel,
            @Schema(requiredMode = REQUIRED) @NotBlank String unit,
            @Schema(requiredMode = REQUIRED) @NotNull List<@NotBlank String> aliases,
            @Schema(requiredMode = REQUIRED) @NotBlank String countingObject,
            @Schema(requiredMode = REQUIRED) @NotBlank String inclusionRule,
            @Schema(requiredMode = REQUIRED) @NotBlank String timeBasis,
            @Schema(requiredMode = REQUIRED) @NotBlank String scopeRule,
            @Schema(requiredMode = REQUIRED) @NotBlank String deduplicationRule,
            @Schema(requiredMode = REQUIRED) @NotNull List<@NotBlank String> pendingQuestions,
            @Schema(requiredMode = REQUIRED) @NotNull @Size(min = 1) List<@NotNull @Valid Example> examples) {
        public Metric {
            aliases = List.copyOf(aliases);
            pendingQuestions = List.copyOf(pendingQuestions);
            examples = List.copyOf(examples);
        }
    }

    @Schema(name = "MetricDefinitionExample")
    public record Example(
            @Schema(requiredMode = REQUIRED) @NotBlank String question,
            @Schema(requiredMode = REQUIRED) @NotBlank String explanation) {}

    /** 口径确认状态，和任务的办理状态无关。 */
    public enum DefinitionStatus { DRAFT, CONFIRMED }
}
