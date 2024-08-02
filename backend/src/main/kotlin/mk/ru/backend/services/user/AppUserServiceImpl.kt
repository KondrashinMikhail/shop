package mk.ru.backend.services.user

import jakarta.transaction.Transactional
import mk.ru.backend.exceptions.ContentNotFoundException
import mk.ru.backend.exceptions.SoftDeletionException
import mk.ru.backend.exceptions.ValidationException
import mk.ru.backend.mappers.AppUserMapper
import mk.ru.backend.persistence.entities.AppUser
import mk.ru.backend.persistence.repositories.AppUserRepo
import mk.ru.backend.services.wallet.WalletService
import mk.ru.backend.utils.AppUserInfo
import mk.ru.backend.utils.Patterns
import mk.ru.backend.web.requests.user.AppUserRegisterRequest
import mk.ru.backend.web.requests.user.PasswordChangeRequest
import mk.ru.backend.web.responses.user.AppUserInfoResponse
import mk.ru.backend.web.responses.user.AppUserRegisterResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class AppUserServiceImpl(
    private val appUserRepo: AppUserRepo,
    private val appUserMapper: AppUserMapper,
    private val walletService: WalletService,
    private val encoder: PasswordEncoder
) : AppUserService {
    private val log: Logger = LoggerFactory.getLogger(this.javaClass.name)

    override fun findInfo(login: String): AppUserInfoResponse =
        appUserMapper.toInfoResponse(findEntityByLogin(login = login, blockedCheck = true))

    override fun register(registerRequest: AppUserRegisterRequest): AppUserRegisterResponse {
        if (appUserRepo.existsByLoginOrMail(registerRequest.login, registerRequest.mail))
            throw ValidationException("User with such credentials already exists")

        if (!Patterns.loginRegex.matches(registerRequest.login))
            throw ValidationException("Login does not match regex")

        if (!Patterns.mailRegex.matches(registerRequest.mail))
            throw ValidationException("Mail does not match regex")

        if (!Patterns.passwordRegex.matches(registerRequest.password))
            throw ValidationException("Password does not match regex")

        val newUser: AppUser = appUserMapper.toEntity(registerRequest)
        newUser.password = newUser.password?.let { encodePassword(it) }
        val registeredUser = appUserRepo.save(newUser)
        log.info("Registered new user with login - '${registeredUser.login}'")

        appUserRepo.save(registeredUser.apply { wallet = walletService.create(newUser) })
        log.info("Attached wallet - '${registeredUser.wallet?.id}' to user - ${registeredUser.login}")

        return appUserMapper.toRegisterResponse(registeredUser)
    }

    @Transactional
    override fun changePassword(login: String, passwordChangeRequest: PasswordChangeRequest) {
        AppUserInfo.checkAccessAllowed(login)

        val user = findEntityByLogin(login = login, blockedCheck = true)
        if (passwordChangeRequest.newPassword != passwordChangeRequest.newPasswordConfirm)
            throw ValidationException("Passwords are not equal")
        if (!Patterns.passwordRegex.matches(passwordChangeRequest.newPassword))
            throw ValidationException("Password does not match regex")

        appUserRepo.save(user.apply { password = encodePassword(passwordChangeRequest.newPassword) })
        log.info("Changed password for user with login - '${user.login}'")
    }

    override fun block(login: String) {
        AppUserInfo.checkAccessAllowed()

        val user = findEntityByLogin(login)
        when (user.blocked) {
            true -> throw SoftDeletionException("User with login - $login is already blocked")
            false -> user.blocked = true
        }

        appUserRepo.save(user)
        log.info("Blocked user with login - $login by user with login - ${AppUserInfo.getAuthenticatedLogin()}")
    }

    override fun restore(login: String) {
        AppUserInfo.checkAccessAllowed()

        val user = findEntityByLogin(login)
        when (user.blocked) {
            true -> user.blocked = false
            false -> throw SoftDeletionException("User with login - $login is not blocked")
        }

        appUserRepo.save(user)
        log.info("Restored user with id - $login")
    }

    override fun getAuthenticated(blockedCheck: Boolean): AppUser =
        findEntityByLogin(AppUserInfo.getAuthenticatedLogin(), blockedCheck)

    override fun findEntityByLogin(login: String, blockedCheck: Boolean): AppUser {
        val user: AppUser = appUserRepo.findById(login)
            .orElseThrow { ContentNotFoundException("User with login - $login not found") }
        if (blockedCheck && user.blocked)
            throw SoftDeletionException("User with login - $login not found")
        return user
    }

    private fun encodePassword(password: String): String = encoder.encode(password).toString()
}