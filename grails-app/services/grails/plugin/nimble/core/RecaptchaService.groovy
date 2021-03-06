/*
 *  Nimble, an extensive application base for Grails
 *  Copyright (C) 2010 Bradley Beddoes
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *  Adapted from the original Recaptcha plugin by Chad Johnston, cjohnston at megatome dot com
 *  http://grails.org/ReCaptcha+Plugin
 */
package grails.plugin.nimble.core

import grails.transaction.NotTransactional
import net.tanesha.recaptcha.ReCaptcha
import net.tanesha.recaptcha.ReCaptchaFactory
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.springframework.beans.factory.InitializingBean

class RecaptchaService implements InitializingBean {

	static transactional = false

	public static final String SESSION_KEY = "grails.plugin.nimble.service.recaptcha"
	public static final String SESSION_ERROR_KEY = "grails.plugin.nimble.service.recaptcha.error"

	String publicKey
	String privateKey
	def includeNoScript
	def secureAPI
	boolean enabled

	GrailsApplication grailsApplication

	void afterPropertiesSet() {
		publicKey = grailsApplication.config.nimble.recaptcha.publickey
		privateKey = grailsApplication.config.nimble.recaptcha.privatekey
		includeNoScript = grailsApplication.config.nimble.recaptcha.noscript
		enabled = grailsApplication.config.nimble.recaptcha.enabled
		secureAPI = grailsApplication.config.nimble.recaptcha.secureapi
	}

	/**
	 * Creates HTML containing all necessary markup for displaying a ReCapture object
	 *
	 * @param session The current session. Used for short term storage of the recaptcha object and any error messages.
	 * @param props Properties used to construct the HTML. See http://recaptcha.net/apidocs/captcha/client.html for valid
	 * properties.
	 *
	 * @return HTML code, suitable for embedding into a webpage.
	 */
	String createCaptcha(session, props) {

		log.debug("Creating ReCaptcha markup")

		if(!enabled) {
			log.debug("ReCaptcha disabled, outputting 'not required' markup")
			return "<em>ReCaptcha is currently not required</em>"
		}

		ReCaptcha recap
		if (secureAPI) {
			recap = ReCaptchaFactory.newSecureReCaptcha(publicKey, privateKey, includeNoScript)
		} else {
			recap = ReCaptchaFactory.newReCaptcha(publicKey, privateKey, includeNoScript)
		}
		session[RecaptchaService.SESSION_KEY] = recap

		log.debug("ReCaptcha enabled, outputting correct markup")
		return recap.createRecaptchaHtml(session[RecaptchaService.SESSION_ERROR_KEY], props)
	}

	/**
	 * Verify a ReCaptcha answer.
	 *
	 * @param session The current session.
	 * @param remoteAddress The address of the browser submitting the answer.
	 * @param params Parameters supplied by the browser.
	 *
	 * @return True if the supplied answer is correct, false otherwise. Returns true if ReCaptcha support is disabled.
	 */
	boolean verifyAnswer(session, remoteAddress, params) {
		if (!isEnabled()) {
			return true
		}

		ReCaptcha recaptcha = session[RecaptchaService.SESSION_KEY]
		if (recaptcha) {
			def response = recaptcha.checkAnswer(remoteAddress, params.recaptcha_challenge_field?.trim(), params.recaptcha_response_field?.trim())
			if (!response.valid) {
				session[RecaptchaService.SESSION_ERROR_KEY] = response.errorMessage
			}

			cleanUp(session)
			return response.valid
		}

		return false
	}

	/**
	 * Determine if the previous verification attempt failed.
	 *
	 * @param session The current session
	 */
	boolean validationFailed(session) {
		return (session[RecaptchaService.SESSION_ERROR_KEY] != null)
	}

	/**
	 * Cleanup recaptcha resources associated with the session.
	 *
	 * @param session The current session.
	 */
	void cleanUp(session) {
		session.removeAttribute RecaptchaService.SESSION_KEY
		session.removeAttribute RecaptchaService.SESSION_ERROR_KEY
	}
}
