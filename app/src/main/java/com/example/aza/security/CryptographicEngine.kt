package com.example.aza.security

import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom

object PaillierCryptosystem {
    private val random = SecureRandom()
    
    class KeyPair(val publicKey: PublicKey, val privateKey: PrivateKey)
    class PublicKey(val n: BigInteger, val g: BigInteger) {
        val nSquare: BigInteger = n.multiply(n)
    }
    class PrivateKey(val lambda: BigInteger, val mu: BigInteger)

    fun generateKeyPair(bitLength: Int = 512): KeyPair {
        val p = BigInteger.probablePrime(bitLength / 2, random)
        val q = BigInteger.probablePrime(bitLength / 2, random)
        
        val n = p.multiply(q)
        val nSquare = n.multiply(n)
        
        val pMinus1 = p.subtract(BigInteger.ONE)
        val qMinus1 = q.subtract(BigInteger.ONE)
        val lambda = pMinus1.multiply(qMinus1).divide(pMinus1.gcd(qMinus1))
        
        val g = n.add(BigInteger.ONE)
        
        val gToLambda = g.modPow(lambda, nSquare)
        val lValue = gToLambda.subtract(BigInteger.ONE).divide(n)
        val mu = lValue.modInverse(n)
        
        return KeyPair(PublicKey(n, g), PrivateKey(lambda, mu))
    }

    fun encrypt(m: BigInteger, pubKey: PublicKey): BigInteger {
        if (m < BigInteger.ZERO || m >= pubKey.n) {
            throw IllegalArgumentException("Message must be 0 <= m < n")
        }
        var r: BigInteger
        do {
            r = BigInteger(pubKey.n.bitLength(), random)
        } while (r >= pubKey.n || r.gcd(pubKey.n) != BigInteger.ONE)
        
        val term1 = pubKey.g.modPow(m, pubKey.nSquare)
        val term2 = r.modPow(pubKey.n, pubKey.nSquare)
        return term1.multiply(term2).mod(pubKey.nSquare)
    }

    fun decrypt(c: BigInteger, pubKey: PublicKey, privKey: PrivateKey): BigInteger {
        val nSquare = pubKey.nSquare
        val cToLambda = c.modPow(privKey.lambda, nSquare)
        val lValue = cToLambda.subtract(BigInteger.ONE).divide(pubKey.n)
        return lValue.multiply(privKey.mu).mod(pubKey.n)
    }

    fun add(c1: BigInteger, c2: BigInteger, pubKey: PublicKey): BigInteger {
        return c1.multiply(c2).mod(pubKey.nSquare)
    }
}

object SchnorrZKP {
    private val random = SecureRandom()
    
    val p = BigInteger("13407807929942597099574024998205846127479365820592393377723561443721764030073546976801874298166903427690031858186486050853753882811946569946433649006084093")
    val g = BigInteger("2")
    val q = p.subtract(BigInteger.ONE).divide(BigInteger.valueOf(2))

    class Proof(
        val g: BigInteger,
        val y: BigInteger,
        val t: BigInteger,
        val s: BigInteger,
        val c: BigInteger
    )

    fun prove(secret: String): Proof {
        val x = sha256ToBigInteger(secret).mod(q)
        val y = g.modPow(x, p)
        
        var r: BigInteger
        do {
            r = BigInteger(256, random)
        } while (r >= q || r <= BigInteger.ONE)
        
        val t = g.modPow(r, p)
        val c = hashChallenge(g, y, t).mod(q)
        val s = r.add(c.multiply(x)).mod(q)
        
        return Proof(g, y, t, s, c)
    }

    fun verify(proof: Proof): Boolean {
        if (proof.s <= BigInteger.ZERO || proof.s >= q) return false
        
        val term1 = proof.g.modPow(proof.s, p)
        val term2 = proof.t.multiply(proof.y.modPow(proof.c, p)).mod(p)
        
        return term1 == term2
    }

    private fun sha256ToBigInteger(input: String): BigInteger {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return BigInteger(1, hashBytes)
    }

    private fun hashChallenge(g: BigInteger, y: BigInteger, t: BigInteger): BigInteger {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(g.toByteArray())
        digest.update(y.toByteArray())
        digest.update(t.toByteArray())
        val hashBytes = digest.digest()
        return BigInteger(1, hashBytes)
    }
}
