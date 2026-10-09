package com.example.data.repository

import com.example.data.local.database.CardDao
import com.example.data.local.entity.CardEntity
import com.example.data.mapper.CardMapper.toDomainList
import com.example.domain.model.Card
import com.example.domain.repository.ICardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CardRepository(private val cardDao: CardDao) : ICardRepository {
    override val allCards: Flow<List<CardEntity>> = cardDao.getAllCards()
    override val cards: Flow<List<Card>> = allCards.map { it.toDomainList() }

    override suspend fun insertCards(cards: List<CardEntity>) {
        cardDao.insertAll(cards)
    }

    override suspend fun deleteAll() {
        cardDao.deleteAll()
    }

    override suspend fun getCardCount(): Int {
        return cardDao.getCardCount()
    }
}
