package com.example.stockmarket.data.repository

import com.opencsv.CSVReader
import com.example.stockmarket.data.csv.CSVParser
import com.example.stockmarket.data.csv.CompanyListingsParser
import com.example.stockmarket.data.local.StockDatabase
//import com.example.stockmarket.data.mapper.toCompanyInfo
import com.example.stockmarket.data.mapper.toCompanyListing
import com.example.stockmarket.data.mapper.toCompanyListingEntity
import com.example.stockmarket.data.remote.StockApi
//import com.example.stockmarket.domain.model.CompanyInfo
import com.example.stockmarket.domain.model.CompanyListing
//import com.example.stockmarket.domain.model.IntradayInfo
import com.example.stockmarket.domain.repository.StockRepository
import com.example.stockmarket.util.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import retrofit2.HttpException
import java.io.IOException
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class StockRepositoryImpl  @Inject constructor(
    private val api:StockApi,
    private val db:StockDatabase,
    private val companyListingsParser: CSVParser<CompanyListing>
): StockRepository {

    private val dao =db.dao

    override suspend fun getCompanyListings(
        fetchFromRemote: Boolean,
        query: String
    ): Flow<Resource<List<CompanyListing>>> {
        return flow {
            emit(Resource.Loading(isLoading = true))

            val localListing=dao.searchCompanyListing(query)

            emit(Resource.Success(
                data= localListing.map { it.toCompanyListing() }
            ))

            val isDbEmpty= localListing.isEmpty() && query.isBlank()
            val shouldJustLoadFromCache=!isDbEmpty && !fetchFromRemote

            if(shouldJustLoadFromCache){
                emit(Resource.Loading(isLoading = false))
                return@flow
            }

            val remoteListings=try {
                val response=api.getListings()
                //response.byteStream()
                companyListingsParser.parse(response.byteStream())

            } catch (e:IOException){
                e.printStackTrace()
                emit(Resource.Error("Couldn't load data"))
                null

            } catch (e:HttpException){
                e.printStackTrace()
                emit(Resource.Error("Couldn't load data"))
                null
            }

            remoteListings?.let { listings->
                dao.clearCompanyListings()
                dao.insertCompanyListings(
                    listings.map { it.toCompanyListingEntity() }
                )
                emit(Resource.Success(
                    data=dao
                        .searchCompanyListing("")
                        .map { it.toCompanyListing() }
                ))
                //emit(Resource.Loading(true))    // this line has problem!


            }



        }

    }


}

