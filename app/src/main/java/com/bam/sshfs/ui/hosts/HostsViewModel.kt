package com.bam.sshfs.ui.hosts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bam.sshfs.R
import com.bam.sshfs.data.db.SshfsDatabase
import com.bam.sshfs.data.model.Host
import com.bam.sshfs.data.model.Identity
import com.bam.sshfs.data.repo.HostRepository
import com.bam.sshfs.data.repo.IdentityRepository
import com.bam.sshfs.net.ConnectionProbe
import com.bam.sshfs.net.ProbeResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** How a "Test connection" attempt is going, for the editor to render. */
sealed interface TestState {
    data object Idle : TestState
    data object Running : TestState
    data class Done(val success: Boolean, val message: String) : TestState
}

/** Drives the Hosts screen: the stored list, the identity picker, CRUD, and the probe. */
class HostsViewModel(
    app: Application,
    private val repo: HostRepository,
    private val identityRepo: IdentityRepository,
    private val probe: ConnectionProbe,
) : AndroidViewModel(app) {

    constructor(app: Application) : this(
        app,
        HostRepository(SshfsDatabase.get(app).hostDao()),
        SshfsDatabase.get(app).let { IdentityRepository(it.identityDao(), it.hostDao()) },
        ConnectionProbe(),
    )

    val hosts: StateFlow<List<Host>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Every stored identity, for the editor's "default identity" picker. */
    val identities: StateFlow<List<Identity>> = identityRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _test = MutableStateFlow<TestState>(TestState.Idle)
    val test: StateFlow<TestState> = _test.asStateFlow()

    fun dismissError() { _error.value = null }

    fun resetTest() { _test.value = TestState.Idle }

    /** Create or update the host described by [form]. */
    fun save(form: HostForm) = work {
        form.validate()?.let { _error.value = message(it); return@work }
        repo.save(form.toHost(System.currentTimeMillis()))
    }

    fun delete(host: Host) = work { repo.delete(host) }

    /**
     * Handshake with the address in [form] without saving it, so the user can
     * check what they typed. Refuses to dial an invalid draft.
     */
    fun testConnection(form: HostForm) {
        form.validate()?.let { _test.value = TestState.Done(false, message(it)); return }
        _test.value = TestState.Running
        viewModelScope.launch {
            _test.value = describe(probe.probe(form.toHost(System.currentTimeMillis())))
        }
    }

    /** Turn a probe outcome into the line shown under the Test button. */
    private fun describe(result: ProbeResult): TestState.Done = when (result) {
        is ProbeResult.Reachable -> TestState.Done(
            success = true,
            message = string(R.string.host_test_ok, result.serverVersion, result.fingerprint) +
                if (result.skippedProxyJump) "\n" + string(R.string.host_test_proxy_ignored) else "",
        )
        is ProbeResult.Failed -> TestState.Done(false, string(R.string.host_test_failed, result.reason))
    }

    private fun message(error: HostFormError): String = string(
        when (error) {
            HostFormError.BLANK_NAME -> R.string.host_error_name
            HostFormError.BLANK_ADDRESS -> R.string.host_error_address
            HostFormError.BAD_PORT -> R.string.host_error_port
            HostFormError.BAD_EXTRA_ARGS -> R.string.host_error_extra_args
        },
    )

    private fun string(id: Int, vararg args: Any): String =
        getApplication<Application>().getString(id, *args)

    /** Run [block] with the busy flag set, funnelling failures into [error]. */
    private fun work(block: suspend () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            try {
                block()
            } catch (e: Exception) {
                _error.value = e.message ?: e.javaClass.simpleName
            } finally {
                _busy.value = false
            }
        }
    }
}
