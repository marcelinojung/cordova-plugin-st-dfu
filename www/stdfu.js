/*global cordova, module*/
module.exports = {
  checkUpdate: function(uuid, success, error) {
    cordova.exec(success, error, 'STDFUPlugin', 'checkUpdate', [uuid]);
  },
  initiateUpdate: function(success, error) {
  	cordova.exec(success, error, 'STDFUPlugin', 'initiateUpdate',[]);
  },
};