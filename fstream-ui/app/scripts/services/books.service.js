/* 
 * Copyright (c) 2015 fStream. All Rights Reserved.
 *
 * Project and contact information: https://bitbucket.org/fstream/fstream
 *
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * Proprietary and confidential.
 */

(function () {
   'use strict';

   angular
      .module('fstream')
      .factory('booksService', booksService);

   booksService.$inject = ['$http', '$q', 'lodash', 'historyService'];

   function booksService($http, $q, _, historyService) {
      var service = {
         getTop: getTop,
         getBook: getBook
      };

      return service;

      function getBook(symbol) {
         return $q.all([
            historyService.getTrades({recent: true}),
            historyService.getQuotes({recent: true}),
            historyService.getSnapshots({recent: true})
         ]).then(function(data){
            return {
               trades: data[0].rows,
               quotes: data[1].rows,
               snapshots: data[2].rows
            }
         });
      }
      
      function getTop() {
         var ids = [10, 11, 12, 13];
         var results = _.map(ids, function (id) {
            return historyService.getLastMetric({
               id: id
            });
         });

         return $q.all(results).then(function (data) {
            return {
               values: data[0],
               trades: data[1],
               orders: data[2],
               ratios: data[3]
            };
         });
      }
   }
})();