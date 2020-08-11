package com.mlreef.rest.exceptions

import org.springframework.http.HttpStatus
import org.springframework.validation.FieldError
import org.springframework.web.bind.annotation.ResponseStatus
import java.util.UUID

enum class ErrorCode(val errorCode: Int, val errorName: String) {
    // authentication and general errors: 1xxx
    NotFound(1404, "Entity not found"),
    NotAllowed(1405, "Method NotAllowed "),
    Conflict(1409, "Entity already exists"),
    AccessDenied(1410, "Access denied exception"),

    // specific user management errors 2xxx
    UserAlreadyExisting(2001, "User already exists"),
    UserNotExisting(2002, "User does not exist"),
    UserBadCredentials(2003, "Username or password is incorrect"),
    GroupNotExisting(2004, "Group does not exist"),
    ProjectNotExisting(2005, "Project does not exist"),
    AccessTokenIsMissing(2006, "Access token is missing"),
    GitlabUserCreationFailed(2101, "Cannot create user in gitlab"),
    GitlabUserTokenCreationFailed(2102, "Cannot create user token in gitlab"),
    GitlabUserNotExisting(2103, "Cannot find user in gitlab via token"),
    GitlabGroupCreationFailed(2104, "Cannot create group in gitlab"),
    GitlabUserAddingToGroupFailed(2105, "Cannot add user to group in gitlab"),
    GitlabProjectCreationFailed(2106, "Cannot create project in gitlab"),
    GitlabProjectUpdateFailed(2107, "Cannot update project in gitlab"),
    GitlabProjectDeleteFailed(2108, "Cannot delete project in gitlab"),
    GitlabVariableCreationFailed(2109, "Cannot create group variable in gitlab"),
    GitlabCommonError(2110, "Gitlab common error"),
    GitlabBadGateway(2111, "Gitlab server is unavailable"),
    GitlabBranchCreationFailed(2112, "Cannot create branch in gitlab"),
    GitlabCommitFailed(2113, "Cannot commit files in gitlab"),
    GitlabProjectAlreadyExists(2114, "Cannot create project in gitlab. Project already exists"),
    GitlabBranchDeletionFailed(2115, "Cannot delete branch in gitlab"),
    GitlabProjectNotExists(2116, "Project not exists in Gitlab"),
    GitlabMembershipDeleteFailed(2117, "Unable to revoke user's membership"),
    GitlabUserModificationFailed(2101, "Cannot modify user in gitlab"),

    // Business errors: 3xxx
    ValidationFailed(3000, "ValidationFailed"),

    // Creating Experiments 31xx
    DataProcessorNotUsable(3101, "DataProcessor cannot be used"),
    ProcessorParameterNotUsable(3102, "ProcessorParameter cannot be used"),
    CommitPipelineScriptFailed(3103, "Could not commit mlreef file"),
    ExperimentCannotBeChanged(3104, "Could not change status of Experiment"),
    ExperimentSlugAlreadyInUse(3105, "Could not change status of Experiment"),
    ExperimentCreationOwnerMissing(3106, "Owner is not provided"),
    ExperimentCreationProjectMissing(3107, "DataProject is not provided"),
    ExperimentCreationDataInstanceMissing(3108, "DataInstance is not provided"),
    ExperimentCreationInvalid(3109, "Could not create Experiment"),

    // Creating Pipelines 32xx
    PipelineSlugAlreadyInUse(3201, "Could not change status"),
    PipelineCreationOwnerMissing(3202, "Owner is not provided"),
    PipelineCreationProjectMissing(3203, "DataProject is not provided"),
    PipelineCreationFilesMissing(3204, "Needs at least 1 Path"),
    PipelineCreationInvalid(3205, "Pipeline could not be created"),

    ProjectCreationFailed(3301, "Could not create project"),
}

@ResponseStatus(code = HttpStatus.BAD_REQUEST, reason = "Operation cannot be executed due to malformed input or invalid states.")
open class RestException(
    val errorCode: Int,
    val errorName: String,
    msg: String? = null,
    cause: Throwable? = null) : RuntimeException(msg, cause) {

    constructor(errorCode: ErrorCode) : this(errorCode.errorCode, errorCode.errorName)
    constructor(errorCode: ErrorCode, msg: String) : this(errorCode.errorCode, errorCode.errorName, msg)
}

@ResponseStatus(code = HttpStatus.BAD_REQUEST, reason = "Operation cannot be executed due to malformed input or invalid states.")
class ValidationException(val validationErrors: Array<FieldError?>) : RestException(ErrorCode.ValidationFailed, validationErrors.joinToString("\n") { it.toString() })

@ResponseStatus(code = HttpStatus.FORBIDDEN, reason = "Bad credentials")
class IncorrectCredentialsException(message: String? = null) : RestException(ErrorCode.AccessDenied, message
    ?: "Access denied")

@ResponseStatus(code = HttpStatus.UNAUTHORIZED, reason = "Unauthorized for the request")
class AccessDeniedException(message: String? = null) : RestException(ErrorCode.AccessDenied, message ?: "Access denied")

@ResponseStatus(code = HttpStatus.NOT_FOUND, reason = "Entity not found")
class NotFoundException(message: String) : RestException(ErrorCode.NotFound, message)

@ResponseStatus(code = HttpStatus.METHOD_NOT_ALLOWED, reason = "Method not allowed or supported")
class MethodNotAllowedException(message: String) : RestException(ErrorCode.NotAllowed, message)

@ResponseStatus(code = HttpStatus.INTERNAL_SERVER_ERROR, reason = "Gitlab not reachable!")
class GitlabConnectException(message: String) : RestException(ErrorCode.NotFound, message)

@ResponseStatus(code = HttpStatus.INTERNAL_SERVER_ERROR, reason = "Gitlab authentication failed")
class GitlabNoValidTokenException(message: String) : RestException(ErrorCode.ValidationFailed, message)

@ResponseStatus(code = HttpStatus.CONFLICT, reason = "Gitlab cannot create entity due to a duplicate conflict:")
class GitlabAlreadyExistingConflictException(errorCode: ErrorCode, message: String) : RestException(errorCode, message)

@ResponseStatus(code = HttpStatus.CONFLICT, reason = "Cannot create entity due to a duplicate conflict:")
class ConflictException(errorCode: ErrorCode, message: String) : RestException(errorCode, message)

@ResponseStatus(code = HttpStatus.CONFLICT, reason = "The state of internal db is inconsistent")
class NotConsistentInternalDb(message: String) : RestException(ErrorCode.Conflict, message)

@ResponseStatus(code = HttpStatus.CONFLICT, reason = "The state of internal db is inconsistent")
class UserAlreadyExistsException(username: String, email: String) : RestException(ErrorCode.UserAlreadyExisting, "User ($username/$email) already exists and cant be created")

open class UnknownUserException(message: String? = null)
    : RestException(ErrorCode.UserNotExisting, message ?: "User is unknown and does not exist")

open class UnknownGroupException(message: String? = null)
    : RestException(ErrorCode.GroupNotExisting, message ?: "Group is unknown and does not exist")

open class UnknownProjectException(message: String? = null)
    : RestException(ErrorCode.ProjectNotExisting, message ?: "Project is unknown and does not exist")

class UserNotFoundException(userId: UUID? = null, userName: String? = null, email: String? = null, personId: UUID? = null, gitlabId: Long? = null, subjectId: UUID? = null)
    : UnknownUserException(generateUserNotFoundMessage(userId, userName, email, personId, gitlabId, subjectId))

class GroupNotFoundException(groupId: UUID? = null, groupName: String? = null, subjectId: UUID? = null, gitlabId: Long? = null, path: String? = null)
    : UnknownGroupException(generateGroupNotFoundMessage(groupId, groupName, subjectId, gitlabId, path))

class ProjectNotFoundException(projectId: UUID? = null, projectName: String? = null, gitlabId: Long? = null, path: String? = null)
    : UnknownProjectException(generateProjectNotFoundMessage(projectId, projectName, gitlabId, path))

class ExperimentCreateException(errorCode: ErrorCode, message: String) : RestException(errorCode, message)
class ExperimentStartException(message: String) : RestException(ErrorCode.CommitPipelineScriptFailed, message)
class ExperimentUpdateException(message: String) : RestException(ErrorCode.ExperimentCannotBeChanged, message)
class ProjectCreationException(errorCode: ErrorCode, message: String) : RestException(errorCode, message)

class ProjectUpdateException(errorCode: ErrorCode, message: String) : RestException(errorCode, message)
class ProjectDeleteException(errorCode: ErrorCode, message: String) : RestException(errorCode, message)

class PipelineCreateException(errorCode: ErrorCode, message: String? = "") : RestException(errorCode, message ?: "")
class PipelineStartException(message: String) : RestException(ErrorCode.CommitPipelineScriptFailed, message)

class BadParametersException(message: String? = null) : RuntimeException(message ?: "Internal exception")

class MissingAccessTokenForUser(accountId: UUID) : RestException(ErrorCode.AccessTokenIsMissing, "No valid/missing access token for account $accountId")

@ResponseStatus(code = HttpStatus.INTERNAL_SERVER_ERROR, reason = "Gitlab is unavailable")
open class GitlabCommonException(
    val statusCode: Int, val responseBodyAsString: String,
    error: ErrorCode? = null, message: String? = null
) : RestException(error ?: ErrorCode.GitlabCommonError, message ?: "Gitlab common exception")

@ResponseStatus(code = HttpStatus.BAD_REQUEST, reason = "Gitlab cannot create entity due to a bad request")
class GitlabBadRequestException(responseBodyAsString: String, error: ErrorCode, message: String) : GitlabCommonException(400, responseBodyAsString, error, message)

@ResponseStatus(code = HttpStatus.BAD_GATEWAY, reason = "Gitlab is unavailable")
class GitlabBadGatewayException(responseBodyAsString: String) : GitlabCommonException(502, responseBodyAsString, ErrorCode.GitlabBadGateway, "Gitlab server is unavailable")

@ResponseStatus(code = HttpStatus.CONFLICT, reason = "Gitlab cannot create entity due to a conflict")
class GitlabConflictException(responseBodyAsString: String, error: ErrorCode, message: String) : GitlabCommonException(409, responseBodyAsString, error, message)

@ResponseStatus(code = HttpStatus.NOT_FOUND, reason = "Gitlab did not find the object")
class GitlabNotFoundException(responseBodyAsString: String, error: ErrorCode, message: String) : GitlabCommonException(404, responseBodyAsString, error, message)

@ResponseStatus(code = HttpStatus.INTERNAL_SERVER_ERROR, reason = "Gitlab authentication failed")
class GitlabAuthenticationFailedException(statusCode: Int, responseBodyAsString: String, error: ErrorCode, message: String) : GitlabCommonException(statusCode, responseBodyAsString, error, message)

@ResponseStatus(code = HttpStatus.INTERNAL_SERVER_ERROR, reason = "Gitlab answered incorrectly")
class GitlabIncorrectAnswerException(message: String) : RestException(500, message)

private fun generateUserNotFoundMessage(userId: UUID?, userName: String?, email: String?,
                                        personId: UUID?, gitlabId: Long?, subjectId: UUID?) = listOf(
    "User id" to userId,
    "Username" to userName,
    "Email Address" to email,
    "Person id" to personId,
    "Gitlab id" to gitlabId,
    "Subject id" to subjectId
)
    .filter { it.second != null }
    .joinToString(prefix = "User not found by ", separator = " and ") {
        "${it.first} = ${it.second}"
    }


private fun generateGroupNotFoundMessage(groupId: UUID?, groupName: String?,
                                         subjectId: UUID?, gitlabId: Long?, path: String?) = listOf(
    "Group id" to groupId,
    "Group name" to groupName,
    "Path" to path,
    "Gitlab id" to gitlabId,
    "Subject id" to subjectId
)
    .filter { it.second != null }
    .joinToString(prefix = "Group not found by ", separator = " and ") {
        "${it.first} = ${it.second}"
    }


private fun generateProjectNotFoundMessage(projectId: UUID?, projectName: String?,
                                           gitlabId: Long?, path: String?) = listOf(
    "Project id" to projectId,
    "Project name" to projectName,
    "Path" to path,
    "Gitlab id" to gitlabId
)
    .filter { it.second != null }
    .joinToString(prefix = "Project not found by ", separator = " and ") {
        "${it.first} = ${it.second}"
    }




