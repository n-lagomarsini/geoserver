.. _nc4:

Installing required NetCDF-4 Native libraries
=============================================
In order to write NetCDF-4 files, you must have the NetCDF-4 C library (version 4.3.1 or above) available on your system, along with all supporting libraries (HDF5, zlib, etc). More info are available `here <http://www.unidata.ucar.edu/software/thredds/current/netcdf-java/reference/netcdf4Clibrary.html/>`_.



Windows instructions
--------------------
#. Download the latest NetCDF4 installer from `here <http://www.unidata.ucar.edu/software/netcdf/docs/winbin.html/>`_
#. Install the executable
#. Make sure to add the *bin* folder of the package you have extracted, to the ``PATH`` environment variable.

If everything has been properly configured, you may notice a similar log message during GeoServer startup:

NetCDF-4 C library loaded (jna_path='null', libname='netcdf').
Netcdf nc_inq_libvers='4.3.1 of Jan 16 2014 15:04:00 $' isProtected=true

Linux instructions
------------------



Requesting a NetCDF4-Classic output file as WCS2.0 output
=========================================================
Specifying application/x-netcdf4 as Format, will return a NetCDF4-Classic output files, provided that the underlying libraries are available.
